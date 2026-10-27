package com.dimakut228.platecam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

class PlateOcr(context: Context, threads: Int, requestedWidth: Int, useXnnpack: Boolean) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val handle: ModelRuntime.SessionHandle
    private val session: OrtSession
    private val inputName: String
    private val inputHeight: Int
    private val inputWidth: Int
    private val keys: List<String>
    private val allowed: BooleanArray

    private val scaledBitmap: Bitmap
    private val canvas: Canvas
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination: Rect
    private val pixels: IntArray
    private val data: FloatArray
    private var outputData = FloatArray(0)

    init {
        val modelFile = ModelAssets.prepare(context, "ocr_ppocrv5_mobile.onnx")
        handle = ModelRuntime.createSession(env, modelFile.absolutePath, threads, useXnnpack)
        session = handle.session
        inputName = session.inputInfo.keys.first()
        val inInfo = session.inputInfo[inputName]?.info as? TensorInfo
        val inShape = inInfo?.shape
        inputHeight = inShape?.getOrNull(2)?.toInt()?.takeIf { it > 0 } ?: 48
        inputWidth = ((requestedWidth.coerceIn(160, 640)) / 8) * 8

        val outInfo = session.outputInfo.values.first().info as? TensorInfo
        val classCount = outInfo?.shape?.getOrNull(2)?.toInt()?.takeIf { it > 0 } ?: 438

        keys = context.assets.open("ppocr_keys.txt").bufferedReader(Charsets.UTF_8).readLines()
        val plateChars = "0123456789ABEKMHOPCTYXCDI".toSet()
        allowed = BooleanArray(classCount)
        allowed[0] = true
        for (i in keys.indices) {
            val key = keys[i]
            if (key.length == 1 && key[0] in plateChars && i + 1 < classCount) {
                allowed[i + 1] = true
            }
        }

        scaledBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        canvas = Canvas(scaledBitmap)
        destination = Rect(0, 0, inputWidth, inputHeight)
        val plane = inputWidth * inputHeight
        pixels = IntArray(plane)
        data = FloatArray(plane * 3)
    }

    fun recognize(bitmap: Bitmap): CtcDecode.Result? {
        canvas.drawBitmap(bitmap, null, destination, scalePaint)
        scaledBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val plane = inputWidth * inputHeight
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 255f
            data[plane + i] = ((p shr 8) and 0xFF) / 255f
            data[plane * 2 + i] = (p and 0xFF) / 255f
        }

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(data),
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )
        val result = try {
            session.run(mapOf(inputName to tensor))
        } finally {
            tensor.close()
        }
        try {
            val out = result[0] as OnnxTensor
            val shape = out.info.shape
            if (shape.size != 3) return null
            val timesteps = shape[1].toInt()
            val classes = shape[2].toInt()
            val buffer = out.floatBuffer
            val remaining = buffer.remaining()
            if (outputData.size < remaining) outputData = FloatArray(remaining)
            buffer.get(outputData, 0, remaining)
            val decoded = CtcDecode.decode(outputData, timesteps, classes, keys, allowed)
            if (decoded.text.isBlank()) return null
            return decoded
        } finally {
            result.close()
        }
    }

    fun close() {
        try {
            handle.close()
        } finally {
            scaledBitmap.recycle()
        }
    }
}
