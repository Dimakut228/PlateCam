package com.dimakut228.platecam

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProfile {
    fun detect(context: Context): PerformanceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return PerformanceTuning.forDevice(
            cores = Runtime.getRuntime().availableProcessors(),
            lowRam = activityManager?.isLowRamDevice == true,
            memoryClassMb = activityManager?.memoryClass ?: 128,
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        )
    }
}
