package cn.traintrip.app

import android.content.Context
import android.content.Intent

const val RAILWAY_PACKAGE = "com.MobileTicket"
enum class AppLaunchResult { OPENED, NOT_INSTALLED, FAILED }

fun launchRailwayApp(context: Context): AppLaunchResult = launchRailwayApp(
    resolve = { context.packageManager.getLaunchIntentForPackage(RAILWAY_PACKAGE) },
    start = { context.startActivity(it) }
)

internal fun launchRailwayApp(resolve: () -> Intent?, start: (Intent) -> Unit): AppLaunchResult = try {
    val intent = resolve()
    if (intent == null) AppLaunchResult.NOT_INSTALLED
    else {
        start(intent)
        AppLaunchResult.OPENED
    }
} catch (_: Exception) {
    AppLaunchResult.FAILED
}
