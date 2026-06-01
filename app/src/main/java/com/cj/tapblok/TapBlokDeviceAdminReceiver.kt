package com.cj.tapblok

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Empty device admin receiver. The only reason TapBlok requests admin status is so the OS
 * greys out Force Stop on the TapBlok app info screen (Android blocks force-stopping an active
 * device admin). We do not actually use any device policies.
 */
class TapBlokDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TapBlok protection enabled.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TapBlok protection disabled.", Toast.LENGTH_SHORT).show()
    }
}
