package com.dimakut228.platecam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

class PlateDetector(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    val inputSize: Int

    init {
        val bytes = context.assets.open("plate_yolov8n.onnx").use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(bytes, options)
        inputName = session.inputInfo.keys.first()
        val info = session.inputInfo[inputName]?.info as? TensorInfo
        val side = info?.shape?.getOrNull(2)?.toInt() ?: 640
        inputSize = if (side > 0) side else 640
    }

    fun detect(bitmap: Bitmap, threshold: Float, iouThreshold: Float = 0.45f): List<Detection> {
        val box = letterbox(bitmap)
        val input = box.bitmap
        val size = inputSize
        val pixels = IntArray(size * size)
        input.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val data = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 255f
            data[plane + i] = ((p shr 8) and 0xFF) / 255f
            data[plane * 2 + i] = (p and 0xFF) / 255f
        }
        if (input !== bitmap) input.recycle()

        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong()))
        val result = try {
            session.run(mapOf(inputName to tensor))
        } finally {
            tensor.close()
        }
        try {
            val out = result[0] as OnnxTensor
            val shape = out.info.shape
            val buffer = out.floatBuffer
            val flat = FloatArray(buffer.remaining())
            buffer.get(flat)
            val raw = YoloDecode.decode(flat, shape, threshold, iouThreshold, size)
            return raw.mapNotNull { mapBack(it, box, bitmap.width, bitmap.height) }
                .filter { d ->
                    val aspect = if (d.height > 0f) d.width / d.height else 0f
                    d.width >= 12f && d.height >= 5f && aspect in 0.7f..8.5f
                }
                .sortedByDescending { it.score }
        } finally {
            result.close()
        }
    }

    fun detectTiled(bitmap: Bitmap, threshold: Float, count: Int): List<Detection> {
        if (count <= 1) return detect(bitmap, threshold)
        val windows = TileGrid.windows(bitmap.width, bitmap.height, count)
        val parts = ArrayList<Pair<TileGrid.Window, List<Detection>>>(windows.size)
        for (window in windows) {
            val sub = Bitmap.createBitmap(bitmap, window.left, window.top, window.width, window.height)
            try {
                parts.add(window to detect(sub, threshold))
            } finally {
                sub.recycle()
            }
        }
        return TileGrid.merge(parts)
    }

    fun close() {
        session.close()
    }

    private data class Letterboxed(
        val bitmap: Bitmap,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    private fun letterbox(source: Bitmap): Letterboxed {
        val target = inputSize
        val scale = min(target.toFloat() / source.width, target.toFloat() / source.height)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, width, height, true)
        val canvasBitmap = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val dx = (target - width) / 2f
        val dy = (target - height) / 2f
        canvas.drawBitmap(resized, dx, dy, null)
        if (resized !== source) resized.recycle()
        return Letterboxed(canvasBitmap, scale, dx, dy)
    }

    private fun mapBack(d: Detection, box: Letterboxed, width: Int, height: Int): Detection? {
        val scale = if (box.scale > 0f) box.scale else 1f
        val left = ((d.left - box.offsetX) / scale).coerceIn(0f, width.toFloat())
        val top = ((d.top - box.offsetY) / scale).coerceIn(0f, height.toFloat())
        val right = ((d.right - box.offsetX) / scale).coerceIn(0f, width.toFloat())
        val bottom = ((d.bottom - box.offsetY) / scale).coerceIn(0f, height.toFloat())
        if (right - left < 1f || bottom - top < 1f) return null
        return Detection(left, top, right, bottom, d.classIndex, d.score)
    }
}
