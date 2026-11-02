package com.lss.lotusaloud

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

object Insets {
    fun apply(activity: Activity, root: View) {
        if (Build.VERSION.SDK_INT < 35 || activity.applicationInfo.targetSdkVersion < 35) return
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
        root.requestApplyInsets()
    }
}
