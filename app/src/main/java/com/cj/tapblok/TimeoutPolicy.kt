package com.cj.tapblok

/**
 * Pure decision logic for Timeout mode and Emergency blocks, kept free of Android dependencies
 * so it can be unit-tested on the JVM.
 */
object TimeoutPolicy {

    /**
     * During Timeout mode everything is blocked EXCEPT: TapBlok itself, critical apps
     * (dialer/launcher/settings), and the apps in the designated allowed group.
     */
    fun shouldBlockInTimeout(
        foregroundPackage: String?,
        selfPackage: String,
        allowedPackages: Set<String>,
        criticalPackages: Set<String>,
    ): Boolean {
        if (foregroundPackage == null) return false
        if (foregroundPackage == selfPackage) return false
        if (foregroundPackage in criticalPackages) return false
        if (foregroundPackage in allowedPackages) return false
        return true
    }

    /** Packages allowed during Timeout mode: members of [timeoutAllowedGroupId] within the blocked-apps map. */
    fun allowedPackages(packageToGroupId: Map<String, Long?>, timeoutAllowedGroupId: Long): Set<String> {
        if (timeoutAllowedGroupId < 0) return emptySet()
        return packageToGroupId.filterValues { it == timeoutAllowedGroupId }.keys
    }

    /**
     * Apps assigned to the timeout-allowed group are always usable — never blocked, even during a
     * normal monitoring session. (They're the allow-list for Timeout mode.)
     */
    fun isAlwaysAllowed(groupId: Long?, timeoutAllowedGroupId: Long): Boolean =
        groupId != null && timeoutAllowedGroupId >= 0 && groupId == timeoutAllowedGroupId

    /** Whether an emergency block for [foregroundPackage] is currently in force. */
    fun isEmergencyBlocked(
        foregroundPackage: String?,
        emergencyBlocks: Map<String, Long>,
        nowMs: Long,
    ): Boolean {
        val expiry = emergencyBlocks[foregroundPackage] ?: return false
        return expiry > nowMs
    }
}
