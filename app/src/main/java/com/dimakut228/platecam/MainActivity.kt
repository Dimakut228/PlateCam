package com.dimakut228.platecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var focusRing: FocusRingView
    private lateinit var statusText: TextView
    private lateinit var plateText: TextView
    private lateinit var plateInfo: TextView

    private lateinit var analysisExecutor: ExecutorService
    @Volatile private var detector: PlateDetector? = null
    @Volatile private var ocr: PlateOcr? = null
    @Volatile private lateinit var prefs: Prefs
    @Volatile private var tracker = PlateTracker()
    @Volatile private var adaptive = AdaptiveRate()
    @Volatile private lateinit var tuning: PerformanceProfile

    @Volatile private var lastAnalysisAt = 0L
    @Volatile private var lastFrameCostMs = 0L
    @Volatile private var lastTilesAt = 0L
    @Volatile private var lastDisplay: PlateRead? = null
    @Volatile private var lastDisplayAt = 0L
    @Volatile private var lastOcrAt = 0L
    @Volatile private var lastOcrBox: Detection? = null
    @Volatile private var rotatedBitmap: Bitmap? = null

    private val modelReady = AtomicBoolean(false)
    @Volatile private var destroyed = false

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    @Volatile private var appliedEv = 0f
    @Volatile private var lastExposureAt = 0L
    @Volatile private var stableExposureFrames = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = getString(R.string.camera_permission_needed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        tuning = DeviceProfile.detect(this)

        previewView = findViewById(R.id.preview)
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        overlay = findViewById(R.id.overlay)
        focusRing = findViewById(R.id.focus_ring)
        statusText = findViewById(R.id.status)
        plateText = findViewById(R.id.plate_number)
        plateInfo = findViewById(R.id.plate_info)

        analysisExecutor = Executors.newSingleThreadExecutor()
        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setupFocus()

        if (!modelReady.get()) loadModels(tuning)
        requestCamera()
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(this)
        tracker = PlateTracker(confirmReads = prefs.confirmReads)
        val targetFps = prefs.analysisFps.coerceAtMost(tuning.maxFps).coerceAtLeast(1)
        val minFps = tuning.minFps.coerceAtMost(targetFps)
        val startFps = if (tuning.tier == PerformanceTier.LOW) minOf(3, targetFps) else targetFps
        adaptive = AdaptiveRate(minFps = minFps, maxFps = targetFps, startFps = startFps)
        adaptive.boost = false
        lastAnalysisAt = 0L
        lastTilesAt = 0L
        lastDisplay = null
        lastDisplayAt = 0L
        lastOcrAt = 0L
        lastOcrBox = null
        appliedEv = 0f
        lastExposureAt = 0L
        stableExposureFrames = 0
        applyExposureCompensation(0f)
    }

    override fun onDestroy() {
        destroyed = true
        modelReady.set(false)
        super.onDestroy()
        val oldDetector = detector
        val oldOcr = ocr
        val oldRotation = rotatedBitmap
        detector = null
        ocr = null
        rotatedBitmap = null
        if (::analysisExecutor.isInitialized) {
            analysisExecutor.shutdown()
            Thread {
                try {
                    if (analysisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        oldDetector?.close()
                        oldOcr?.close()
                        oldRotation?.recycle()
                    }
                } catch (ignored: InterruptedException) {
                }
            }.apply { isDaemon = true }.start()
        } else {
            oldDetector?.close()
            oldOcr?.close()
            oldRotation?.recycle()
        }
    }

    private fun loadModels(profile: PerformanceProfile) {
        runOnUiThread { statusText.text = getString(R.string.loading_models) }
        analysisExecutor.execute {
            var loadedDetector: PlateDetector? = null
            var loadedOcr: PlateOcr? = null
            try {
                loadedDetector = PlateDetector(this, profile.threads, prefs.xnnpack)
                loadedOcr = PlateOcr(this, profile.threads, profile.ocrWidth, prefs.xnnpack)
                if (destroyed) {
                    loadedDetector.close()
                    loadedOcr.close()
                    return@execute
                }
                detector = loadedDetector
                ocr = loadedOcr
                modelReady.set(true)
                runOnUiThread {
                    statusText.text = "PlateCam · ${loadedDetector.inputSize} px · ${profile.tier.label} · готово"
                }
            } catch (t: Throwable) {
                loadedDetector?.close()
                loadedOcr?.close()
                runOnUiThread {
                    statusText.text = getString(R.string.model_missing)
                }
            }
        }
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (!::analysisExecutor.isInitialized || analysisExecutor.isShutdown) {
            analysisExecutor = Executors.newSingleThreadExecutor()
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(tuning.analysisWidth, tuning.analysisHeight))
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                setupCameraModes(camera.cameraControl)
            } catch (t: Throwable) {
                runOnUiThread { statusText.text = t.message ?: t.javaClass.simpleName }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupFocus() {
        val root = findViewById<View>(R.id.root)
        val panel = findViewById<View>(R.id.panel)
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val panelBounds = Rect()
                panel.getHitRect(panelBounds)
                if (panelBounds.contains(event.x.toInt(), event.y.toInt())) {
                    return@setOnTouchListener false
                }
                focusRing.show(event.x, event.y)
                focusAt(event.x, event.y)
                true
            } else {
                false
            }
        }
    }

    private fun focusAt(x: Float, y: Float) {
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or
                    FocusMeteringAction.FLAG_AE or
                    FocusMeteringAction.FLAG_AWB
            ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
            cameraControl?.startFocusAndMetering(action)
        } catch (t: Throwable) {
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun setupCameraModes(control: CameraControl) {
        try {
            val camera2 = Camera2CameraControl.from(control)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO
                )
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                .build()
            camera2.setCaptureRequestOptions(options)
        } catch (t: Throwable) {
        }
    }

    private fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < adaptive.intervalMs() || !modelReady.get()) {
            image.close()
            return
        }
        lastAnalysisAt = now
        val started = System.currentTimeMillis()
        try {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation % 360 == 0) bitmap else rotateForAnalysis(bitmap, rotation)
            try {
                processFrame(upright)
            } finally {
                bitmap.recycle()
            }
        } catch (t: Throwable) {
            val message = t.message ?: t.javaClass.simpleName
            runOnUiThread { statusText.text = message }
        } finally {
            lastFrameCostMs = System.currentTimeMillis() - started
            adaptive.report(lastFrameCostMs)
            image.close()
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        maybeTuneExposure(bitmap)
        val activeDetector = detector ?: return
        val activeOcr = ocr ?: return
        val now = System.currentTimeMillis()
        val threshold = prefs.threshold
        var results = activeDetector.detect(bitmap, threshold)
        if (results.isEmpty() && prefs.tiles && tuning.allowTiles &&
            lastFrameCostMs > 0L && lastFrameCostMs <= tuning.tileMaxCostMs &&
            now - lastTilesAt > 1200L
        ) {
            lastTilesAt = now
            results = activeDetector.detectTiled(bitmap, threshold, 4)
        }

        val best = results.maxByOrNull { it.score }
        // Пока номер не найден — обрабатываем кадры подряд (AdaptiveRate.boost).
        adaptive.boost = best == null
        var read: PlateRead? = null
        val tracked: PlateTracker.Tracked?
        val wasConfirmed = lastDisplay?.confirmed == true
        if (best != null) {
            val previousBox = lastOcrBox
            val shouldOcr = previousBox == null ||
                !samePlate(previousBox, best) ||
                now - lastOcrAt >= if (wasConfirmed) tuning.ocrIntervalMs else 120L
            if (shouldOcr) {
                lastOcrAt = now
                lastOcrBox = best
                val crop = BitmapUtils.crop(bitmap, best)
                if (crop != null) {
                    try {
                        val ocrResult = activeOcr.recognize(crop)
                        if (ocrResult != null && ocrResult.text.isNotBlank()) {
                            val hint = PlateHint(
                                country = prefs.country,
                                yellow = BitmapUtils.isYellow(crop),
                                text = ocrResult.text
                            )
                            read = PlateParser.parse(
                                ocrResult.text,
                                ocrResult.confidence,
                                hint,
                                tuning.parserVariants
                            )
                        }
                    } finally {
                        crop.recycle()
                    }
                }
            }
            tracked = tracker.update(best, read)
        } else {
            lastOcrBox = null
            tracked = tracker.miss()
        }

        val display = tracked?.read ?: lastDisplay?.takeIf { now - lastDisplayAt <= 2500L }
        if (display != null) {
            lastDisplay = display
            lastDisplayAt = now
        }
        val box = best ?: tracked?.detection
        val confirmed = tracked?.confirmed == true
        val frameWidth = bitmap.width
        val frameHeight = bitmap.height
        val count = results.size
        val fps = adaptive.fps
        val cost = lastFrameCostMs

        runOnUiThread {
            if (box != null) {
                val label = display?.display ?: "номер"
                overlay.show(listOf(box), listOf(label), frameWidth, frameHeight)
            } else {
                overlay.clear()
            }
            if (display != null) {
                plateText.text = display.display
                val confidence = (display.confidence * 100f).roundToInt().coerceIn(0, 100)
                val state = if (confirmed) "" else " · проверка"
                plateInfo.text = "${display.country.label} · ${display.kind} · $confidence%$state"
            } else {
                plateText.text = getString(R.string.searching_plate)
                plateInfo.text = getString(R.string.search_hint)
            }
            statusText.text = "PlateCam · $fps к/с · $cost мс · $count" + if (adaptive.boost) " · поиск" else ""
        }
    }

    private fun rotateForAnalysis(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        val targetWidth = if (normalized % 180 == 0) source.width else source.height
        val targetHeight = if (normalized % 180 == 0) source.height else source.width
        var target = rotatedBitmap
        if (target == null || target.isRecycled ||
            target.width != targetWidth || target.height != targetHeight
        ) {
            target?.recycle()
            val fresh = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            fresh.density = source.density
            rotatedBitmap = fresh
            target = fresh
        }
        val output = target ?: return source
        output.density = source.density
        val canvas = Canvas(output)
        canvas.save()
        when (normalized) {
            90 -> {
                canvas.translate(targetWidth.toFloat(), 0f)
                canvas.rotate(90f)
            }
            180 -> {
                canvas.translate(targetWidth.toFloat(), targetHeight.toFloat())
                canvas.rotate(180f)
            }
            270 -> {
                canvas.translate(0f, targetHeight.toFloat())
                canvas.rotate(270f)
            }
        }
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.restore()
        return output
    }

    private fun samePlate(a: Detection, b: Detection): Boolean {
        if (a.iou(b) >= 0.35f) return true
        val limit = maxOf(a.width, a.height) * 0.6f
        return a.distanceTo(b) <= limit
    }

    private fun maybeTuneExposure(bitmap: Bitmap) {
        if (!prefs.autoExposure) {
            stableExposureFrames = 0
            if (appliedEv != 0f) {
                appliedEv = 0f
                runOnUiThread { applyExposureCompensation(0f) }
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastExposureAt < 900L) return
        lastExposureAt = now

        val cols = 24
        val rows = 16
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return
        var sum = 0L
        var clipped = 0
        var count = 0
        for (row in 0 until rows) {
            val y = ((row + 0.5f) * height / rows).toInt().coerceIn(0, height - 1)
            for (col in 0 until cols) {
                val x = ((col + 0.5f) * width / cols).toInt().coerceIn(0, width - 1)
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (r * 77 + g * 150 + b * 29) shr 8
                sum += luma
                if (luma >= 248) clipped++
                count++
            }
        }
        if (count == 0) return
        val average = sum.toFloat() / (count * 255f)
        val clippedFraction = clipped.toFloat() / count

        val overexposed = clippedFraction > 0.08f || average > 0.72f
        val underexposed = clippedFraction < 0.015f && average < 0.20f
        when {
            overexposed -> {
                stableExposureFrames = 0
                val next = (appliedEv - 0.5f).coerceAtLeast(-2f)
                if (next < appliedEv - 0.1f) {
                    appliedEv = next
                    runOnUiThread { applyExposureCompensation(next) }
                }
            }
            underexposed -> {
                stableExposureFrames = 0
                val next = (appliedEv + 0.5f).coerceAtMost(1.0f)
                if (next > appliedEv + 0.1f) {
                    appliedEv = next
                    runOnUiThread { applyExposureCompensation(next) }
                }
            }
            else -> {
                stableExposureFrames++
                if (stableExposureFrames >= 4 && appliedEv != 0f) {
                    stableExposureFrames = 0
                    appliedEv = if (appliedEv > 0f) {
                        (appliedEv - 0.5f).coerceAtLeast(0f)
                    } else {
                        (appliedEv + 0.5f).coerceAtMost(0f)
                    }
                    val relaxed = appliedEv
                    runOnUiThread { applyExposureCompensation(relaxed) }
                }
            }
        }
    }

    private fun applyExposureCompensation(ev: Float) {
        val info = cameraInfo ?: return
        val control = cameraControl ?: return
        try {
            val state = info.exposureState
            if (!state.isExposureCompensationSupported) return
            val step = state.exposureCompensationStep
            if (step.denominator == 0) return
            val stepValue = step.numerator.toFloat() / step.denominator
            if (stepValue <= 0f) return
            val range = state.exposureCompensationRange
            val index = (ev / stepValue).roundToInt().coerceIn(range.lower, range.upper)
            control.setExposureCompensationIndex(index)
        } catch (t: Throwable) {
        }
    }
}
