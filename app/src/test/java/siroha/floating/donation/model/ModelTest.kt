package siroha.floating.donation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayConfigTest {

    @Test
    fun `default config has sensible values`() {
        val config = OverlayConfig()
        assertEquals("", config.name)
        assertEquals("", config.url)
        assertEquals(OverlayType.WEB, config.type)
        assertFalse(config.isActive)
        assertFalse(config.isLocked)
        assertTrue(config.isVisible)
        assertEquals(400, config.width)
        assertEquals(300, config.height)
        assertEquals(1.0f, config.opacity)
        assertTrue(config.keepPosition)
        assertTrue(config.alwaysOnTop)
        assertEquals(OverlayOrientation.FOLLOW_DEVICE, config.orientation)
    }

    @Test
    fun `each config gets unique id`() {
        val config1 = OverlayConfig()
        val config2 = OverlayConfig()
        assertNotEquals(config1.id, config2.id)
    }

    @Test
    fun `copy preserves id`() {
        val config = OverlayConfig(name = "Test")
        val copy = config.copy(name = "Updated")
        assertEquals(config.id, copy.id)
        assertEquals("Updated", copy.name)
    }
}

class AppLayoutTest {

    @Test
    fun `default layout has sensible values`() {
        val layout = AppLayout()
        assertEquals("", layout.overlayId)
        assertEquals("", layout.packageName)
        assertEquals(100, layout.positionX)
        assertEquals(100, layout.positionY)
        assertTrue(layout.isEnabled)
    }

    @Test
    fun `each layout gets unique id`() {
        val layout1 = AppLayout()
        val layout2 = AppLayout()
        assertNotEquals(layout1.id, layout2.id)
    }
}

class AppSettingsTest {

    @Test
    fun `default settings have sensible values`() {
        val settings = AppSettings()
        assertEquals(400, settings.defaultWidth)
        assertEquals(300, settings.defaultHeight)
        assertEquals(1.0f, settings.defaultOpacity)
        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.mediaPlaybackEnabled)
        assertTrue(settings.cacheEnabled)
        assertTrue(settings.cookiesEnabled)
        assertTrue(settings.hardwareAcceleration)
        assertFalse(settings.debugLogging)
        assertFalse(settings.onboardingCompleted)
        assertEquals(Int.MAX_VALUE, settings.maxOverlays)
    }
}

class OverlayPresetTest {

    @Test
    fun `presets list is not empty`() {
        assertTrue(OverlayPreset.presets.isNotEmpty())
    }

    @Test
    fun `presets have valid dimensions`() {
        OverlayPreset.presets.forEach { preset ->
            assertTrue("Preset ${preset.name} width should be positive", preset.defaultWidth > 0)
            assertTrue("Preset ${preset.name} height should be positive", preset.defaultHeight > 0)
            assertTrue("Preset ${preset.name} name should not be blank", preset.name.isNotBlank())
        }
    }
}
