package com.dimakut228.platecam

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ModelAssets {

    fun prepare(context: Context, assetName: String): File {
        val root = File(context.filesDir, "models")
        val current = File(root, "v${BuildConfig.VERSION_CODE}")
        if (!current.exists()) {
            current.mkdirs()
            root.listFiles()?.forEach { old ->
                if (old != current) old.deleteRecursively()
            }
        }

        val target = File(current, assetName)
        val expectedLength = try {
            context.assets.openFd(assetName).use { it.length }
        } catch (t: Throwable) {
            -1L
        }
        if (target.exists() && target.length() > 0L &&
            (expectedLength <= 0L || target.length() == expectedLength)
        ) {
            return target
        }

        val temp = File(current, "$assetName.tmp")
        temp.delete()
        context.assets.open(assetName).use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }

        target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }
}
