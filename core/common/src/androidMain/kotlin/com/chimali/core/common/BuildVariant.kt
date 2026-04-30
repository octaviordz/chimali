package com.chimali.core.common

import co.touchlab.kermit.Logger
import java.lang.reflect.InvocationTargetException

/**
 * Android implementation of build variant detection.
 * Uses reflection to detect the debuggable flag from the current application context
 * without requiring an explicit Context parameter (supports AKM single-variant architecture).
 */
actual val isDebug: Boolean by lazy {
    try {
        val activityThread = Class.forName("android.app.ActivityThread")
        val currentApplication = activityThread.getMethod("currentApplication")
        val app = currentApplication.invoke(null) as? android.app.Application
        if (app != null) {
            (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } else {
            // Fallback: If we can't get the app context, check system property
            // (useful for some test environments)
            System.getProperty("chimali.debug") == "true"
        }
    } catch (e: ClassNotFoundException) {
        Logger.e(e) { "Failed to determine build variant via reflection: ActivityThread class not found" }
        false
    } catch (e: NoSuchMethodException) {
        Logger.e(e) { "Failed to determine build variant via reflection: currentApplication method not found" }
        false
    } catch (e: IllegalAccessException) {
        Logger.e(e) { "Failed to determine build variant via reflection: access denied" }
        false
    } catch (e: InvocationTargetException) {
        Logger.e(e) { "Failed to determine build variant via reflection: invocation failed" }
        false
    }
}
