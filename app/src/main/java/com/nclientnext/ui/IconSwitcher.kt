package com.nclientnext.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches the launcher entry between the normal Nclient-R icon and the calculator disguise. */
object IconSwitcher {
    private fun cn(ctx: Context, name: String) = ComponentName(ctx.packageName, "com.nclientnext.$name")

    fun isCalculator(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(cn(ctx, "LauncherCalculator")) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setCalculator(ctx: Context, calculator: Boolean) {
        val pm = ctx.packageManager
        // Enable the new one first so there is never a moment with no launcher icon
        pm.setComponentEnabledSetting(
            cn(ctx, if (calculator) "LauncherCalculator" else "LauncherDefault"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            cn(ctx, if (calculator) "LauncherDefault" else "LauncherCalculator"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
    }
}
