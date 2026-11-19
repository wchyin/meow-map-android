package cn.edu.meowmap

import cn.edu.meowmap.data.publicAvatarUrl
import cn.edu.meowmap.ui.avatarCropRect
import org.junit.Assert.*
import org.junit.Test

class AvatarTest {
    @Test fun publicImagesKeepTheirServiceUrl() {
        val root = "https://example.com/meow-api"
        val route = "/uploads/12345678-1234-1234-1234-123456789abc"
        assertEquals(root + route, publicAvatarUrl(route, root))
        assertEquals(root + route, publicAvatarUrl(root + route, root))
        assertEquals("builtin:mint", publicAvatarUrl("builtin:mint", root))
        assertNull(publicAvatarUrl("https://evil.example" + route, root))
        assertNull(publicAvatarUrl("", root))
    }
    @Test fun cropStaysInsidePortraitAndLandscape() {
        for ((w, h) in listOf(800 to 1200, 1200 to 800, 1 to 1)) {
            for (zoom in listOf(1f, 2f, 4f)) for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) {
                val r = avatarCropRect(w, h, zoom, x, y)
                assertTrue(r.side > 0 && r.left >= 0 && r.top >= 0)
                assertTrue(r.left + r.side <= w && r.top + r.side <= h)
            }
        }
        assertEquals(200, avatarCropRect(1200, 800, 1f, .5f, .5f).left)
        assertEquals(400, avatarCropRect(1200, 800, 2f, .5f, .5f).side)
    }
}
