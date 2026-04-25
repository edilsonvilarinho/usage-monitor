package com.usagemonitor

import java.util.prefs.Preferences

object AutoStartManager {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "UsageMonitor"

    fun isAutoStartEnabled(): Boolean {
        return runCatching {
            val prefs = Preferences.userRoot().node(RUN_KEY)
            prefs.get(APP_NAME, null) != null
        }.getOrDefault(false)
    }

    fun setAutoStart(enabled: Boolean): Boolean {
        return runCatching {
            val prefs = Preferences.userRoot().node(RUN_KEY)
            if (enabled) {
                val exePath = "\"${System.getProperty("user.dir")}\\UsageMonitor.exe\""
                prefs.put(APP_NAME, exePath)
            } else {
                prefs.remove(APP_NAME)
            }
            true
        }.getOrDefault(false)
    }

    fun syncFromPreference(enabled: Boolean) {
        setAutoStart(enabled)
    }
}