package cn.edu.meowmap

import cn.edu.meowmap.data.PhotoListConverter
import org.junit.Assert.*
import org.junit.Test

class PhotoListConverterTest {
    private val converter = PhotoListConverter()
    @Test fun preservesChineseQuotesAndBackslashes() {
        val paths = listOf("/photos/猫咪.jpg", "C:\\photos\\cat\"one.jpg", "/photos/new\nline.jpg")
        assertEquals(paths, converter.decode(converter.encode(paths)))
    }
    @Test fun emptyDraftRoundTrips() { assertEquals(emptyList<String>(), converter.decode(converter.encode(emptyList()))) }
    @Test fun preservesPhotoOrder() {
        val paths = listOf("/3.jpg", "/1.jpg", "/2.jpg")
        assertEquals(paths, converter.decode(converter.encode(paths)))
    }
}
