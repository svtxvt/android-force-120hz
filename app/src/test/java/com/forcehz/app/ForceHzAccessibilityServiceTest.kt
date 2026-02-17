package com.forcehz.app

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForceHzAccessibilityServiceTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var service: ForceHzAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(ForceHzAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }

        service = Robolectric.buildService(ForceHzAccessibilityService::class.java)
            .create()
            .get()
    }

    @After
    fun tearDown() {
        try {
            service.onDestroy()
        } catch (_: Exception) {
        }
        prefs.edit { clear() }
    }

    @Test
    fun onServiceConnected_withRestoreFlag_startsAnimationAndClearsPendingRestore() {
        prefs.edit {
            putBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, false)
            putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, true)
        }

        connectService()

        assertTrue(service.isAnimationEnabled())
        assertTrue(prefs.getBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, false))
        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, true))
    }

    @Test
    fun stopForceRefresh_clearsAnimationAndRestoreFlags() {
        connectService()
        service.startForceRefresh()
        assertTrue(service.isAnimationEnabled())

        service.stopForceRefresh()

        assertFalse(service.isAnimationEnabled())
        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, true))
        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, true))
    }

    @Test
    fun toggleFpsOverlay_updatesPreference() {
        connectService()
        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_SHOW_FPS, false))

        service.toggleFpsOverlay()
        assertTrue(prefs.getBoolean(ForceHzAccessibilityService.PREF_SHOW_FPS, false))

        service.toggleFpsOverlay()
        assertFalse(prefs.getBoolean(ForceHzAccessibilityService.PREF_SHOW_FPS, true))
    }

    @Test
    fun onDestroy_marksServiceNotRunning() {
        connectService()
        assertTrue(ForceHzAccessibilityService.isRunning)

        service.onDestroy()

        assertFalse(ForceHzAccessibilityService.isRunning)
    }

    private fun connectService() {
        val method = ForceHzAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)
    }
}
