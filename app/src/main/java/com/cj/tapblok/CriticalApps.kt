package com.cj.tapblok

/**
 * Packages that must never be blocked — blocking any of these could soft-brick the device
 * (no way to make a call, reach Settings, or get back to the home screen).
 *
 * Used in two places:
 *  - [AppSelectionActivity] filters these out of the selectable blocked-apps list.
 *  - [AppMonitoringService] exempts them in Timeout mode (which blocks everything by default)
 *    and the Emergency tag handler refuses to emergency-block them.
 */
object CriticalApps {
    val PACKAGES: Set<String> = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.sonyericsson.android.socialphonebook",
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )
}
