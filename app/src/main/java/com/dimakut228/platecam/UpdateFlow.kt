package com.dimakut228.platecam

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Проверка обновления с диалогом установки. Используется MainActivity
 * (автопроверка раз в сутки) и настройками (ручная проверка).
 */
object UpdateFlow {

    /** Результат проверки для обратной связи (например, Toast в настройках). */
    enum class Result {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        FAILED
    }

    /**
     * Проверяет наличие новой сборки и предлагает установку.
     * [onFinished] вызывается из фонового потока с итогом проверки
     * (вызывается до показа диалога — используйте для Toast).
     */
    fun check(activity: AppCompatActivity, onFinished: ((Result) -> Unit)? = null) {
        Thread {
            val current = BuildConfig.VERSION_CODE.toLong()
            // APK уже скачан в прошлый раз — предлагаем установить сразу.
            UpdateChecker.downloadedUpdate(activity, current)?.let { apk ->
                onFinished?.invoke(Result.UPDATE_AVAILABLE)
                promptInstall(activity, apk)
                return@Thread
            }
            val release = UpdateChecker.fetchLatest()
            if (release == null) {
                onFinished?.invoke(Result.FAILED)
                return@Thread
            }
            if (!UpdateChecker.isNewer(release.build, current)) {
                onFinished?.invoke(Result.UP_TO_DATE)
                return@Thread
            }
            val apk = UpdateChecker.download(activity, release)
            if (apk == null) {
                onFinished?.invoke(Result.FAILED)
                return@Thread
            }
            onFinished?.invoke(Result.UPDATE_AVAILABLE)
            promptInstall(activity, apk)
        }.apply { isDaemon = true }.start()
    }

    private fun promptInstall(activity: AppCompatActivity, apk: File) {
        val intent = UpdateChecker.installIntent(activity, apk) ?: return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available))
                .setMessage(
                    activity.getString(
                        R.string.update_message,
                        apk.nameWithoutExtension.removePrefix("PlateCam-")
                    )
                )
                .setPositiveButton(activity.getString(R.string.update_install)) { _, _ ->
                    activity.startActivity(intent)
                }
                .setNegativeButton(activity.getString(R.string.update_later), null)
                .show()
        }
    }
}
