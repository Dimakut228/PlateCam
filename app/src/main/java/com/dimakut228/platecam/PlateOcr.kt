package com.dimakut228.platecam

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

class PlateOcr(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val inputHeight: Int
    private val inputWidth: Int
    private val keys: List<String>
    private val allowed: BooleanArray

    init {
        val bytes = context.assets.open("ocr_ppocrv5_mobile.onnx").use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(bytes, options)
        inputName = session.inputInfo.keys.first()
        val inInfo = session.inputInfo[inputName]?.info as? TensorInfo
        val inShape = inInfo?.shape
        inputHeight = inShape?.getOrNull(2)?.toInt()?.takeIf { it > 0 } ?: 48
        inputWidth = 320

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
    }

    fun recognize(bitmap: Bitmap): CtcDecode.Result? {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val plane = inputWidth * inputHeight
        val pixels = IntArray(plane)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled !== bitmap) scaled.recycle()

        val data = FloatArray(plane * 3)
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
            val flat = FloatArray(buffer.remaining())
            buffer.get(flat)
            val decoded = CtcDecode.decode(flat, timesteps, classes, keys, allowed)
            if (decoded.text.isBlank()) return null
            return decoded
        } finally {
            result.close()
        }
    }

    fun close() {
        session.close()
    }
}
