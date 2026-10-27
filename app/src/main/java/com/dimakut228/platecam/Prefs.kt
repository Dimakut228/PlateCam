package com.dimakut228.platecam

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Prefs(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val threshold: Float get() = prefs.getString("threshold", "0.35")?.toFloatOrNull() ?: 0.35f
    val analysisFps: Int get() = prefs.getString("analysis_fps", "6")?.toIntOrNull() ?: 6
    val confirmReads: Int get() = prefs.getString("confirm_reads", "2")?.toIntOrNull() ?: 2
    val tiles: Boolean get() = prefs.getBoolean("tiles", true)
    val autoExposure: Boolean get() = prefs.getBoolean("auto_exposure", true)
    val country: Country
        get() = when (prefs.getString("country", "all")) {
            "ru" -> Country.RU
            "by" -> Country.BY
            else -> Country.UNKNOWN
        }
}
