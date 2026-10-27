package com.dimakut228.platecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
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
    private lateinit var statusText: TextView
    private lateinit var plateText: TextView
    private lateinit var plateInfo: TextView

    private lateinit var analysisExecutor: ExecutorService
    private var detector: PlateDetector? = null
    private var ocr: PlateOcr? = null
    private lateinit var prefs: Prefs
    private var tracker = PlateTracker()
    private var adaptive = AdaptiveRate()

    private var lastAnalysisAt = 0L
    private var lastFrameCostMs = 0L
    private var lastTilesAt = 0L
    private var lastDisplay: PlateRead? = null
    private var lastDisplayAt = 0L

    private val modelReady = AtomicBoolean(false)
    @Volatile private var destroyed = false

    private var cameraControl: CameraControl? = null

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

        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        statusText = findViewById(R.id.status)
        plateText = findViewById(R.id.plate_number)
        plateInfo = findViewById(R.id.plate_info)

        analysisExecutor = Executors.newSingleThreadExecutor()
        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (!modelReady.get()) loadModels()
        requestCamera()
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(this)
        tracker = PlateTracker(confirmReads = prefs.confirmReads)
        val targetFps = prefs.analysisFps.coerceIn(3, 8)
        adaptive = AdaptiveRate(minFps = 3, maxFps = targetFps, startFps = targetFps)
        lastAnalysisAt = 0L
        lastTilesAt = 0L
        lastDisplay = null
        lastDisplayAt = 0L
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
        detector?.close()
        detector = null
        ocr?.close()
        ocr = null
    }

    private fun loadModels() {
        analysisExecutor.execute {
            var loadedDetector: PlateDetector? = null
            var loadedOcr: PlateOcr? = null
            try {
                loadedDetector = PlateDetector(this)
                loadedOcr = PlateOcr(this)
                if (destroyed) {
                    loadedDetector.close()
                    loadedOcr.close()
                    return@execute
                }
                detector = loadedDetector
                ocr = loadedOcr
                modelReady.set(true)
                runOnUiThread {
                    statusText.text = "PlateCam · ${loadedDetector.inputSize} px · готово"
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
                    .setTargetResolution(Size(1280, 720))
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
                setupFocus()
            } catch (t: Throwable) {
                runOnUiThread { statusText.text = t.message ?: t.javaClass.simpleName }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupFocus() {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                try {
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    cameraControl?.startFocusAndMetering(action)
                } catch (t: Throwable) {
                }
                true
            } else {
                false
            }
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
            val upright = BitmapUtils.rotate(bitmap, rotation)
            if (upright !== bitmap) bitmap.recycle()
            try {
                processFrame(upright)
            } finally {
                upright.recycle()
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
        val activeDetector = detector ?: return
        val activeOcr = ocr ?: return
        val now = System.currentTimeMillis()
        val threshold = prefs.threshold
        var results = activeDetector.detect(bitmap, threshold)
        if (results.isEmpty() && prefs.tiles && now - lastTilesAt > 1200L) {
            lastTilesAt = now
            results = activeDetector.detectTiled(bitmap, threshold, 4)
        }

        val best = results.maxByOrNull { it.score }
        var read: PlateRead? = null
        val tracked: PlateTracker.Tracked?
        if (best != null) {
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
                        read = PlateParser.parse(ocrResult.text, ocrResult.confidence, hint)
                    }
                } finally {
                    crop.recycle()
                }
            }
            tracked = tracker.update(best, read)
        } else {
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
            statusText.text = "PlateCam · $fps к/с · $cost мс · $count"
        }
    }
}
