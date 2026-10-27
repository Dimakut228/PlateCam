package com.dimakut228.platecam

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

object ModelRuntime {

    class SessionHandle(
        val session: OrtSession,
        val options: OrtSession.SessionOptions
    ) {
        fun close() {
            try {
                session.close()
            } finally {
                options.close()
            }
        }
    }

    fun createSession(
        environment: OrtEnvironment,
        modelPath: String,
        threads: Int
    ): SessionHandle {
        val xnnpackOptions = baseOptions(threads)
        try {
            xnnpackOptions.addXnnpack(emptyMap())
            return SessionHandle(
                environment.createSession(modelPath, xnnpackOptions),
                xnnpackOptions
            )
        } catch (t: Throwable) {
            try {
                xnnpackOptions.close()
            } catch (ignored: Throwable) {
            }
        }

        val cpuOptions = baseOptions(threads)
        return SessionHandle(
            environment.createSession(modelPath, cpuOptions),
            cpuOptions
        )
    }

    private fun baseOptions(threads: Int): OrtSession.SessionOptions =
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads.coerceAtLeast(1))
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
}
