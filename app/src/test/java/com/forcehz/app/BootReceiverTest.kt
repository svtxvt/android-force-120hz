package com.forcehz.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(ForceHzAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            null
        )
    }

    @Test
    fun bootCompleted_withAnimationEnabled_setsRestoreFlag() {
        prefs.edit {
            putBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, true)
            putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false)
        }

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false))
    }

    @Test
    fun packageReplaced_withAnimationEnabled_setsRestoreFlag() {
        prefs.edit {
            putBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, true)
            putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false)
        }

        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertTrue(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false))
    }

    @Test
    fun bootCompleted_withAnimationDisabled_doesNotSetRestoreFlag() {
        prefs.edit {
            putBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, false)
            putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false)
        }

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false))
    }

    @Test
    fun unrelatedAction_doesNotChangePrefs() {
        prefs.edit {
            putBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, true)
            putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false)
        }

        BootReceiver().onReceive(context, Intent("com.forcehz.app.UNRELATED"))

        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, false))
    }
}
