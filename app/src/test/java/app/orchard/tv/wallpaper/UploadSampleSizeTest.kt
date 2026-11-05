package app.orchard.tv.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadSampleSizeTest {
    @Test fun preservesNormalLandscapeAndDownsamplesTallImages() {
        assertEquals(1, uploadSampleSize(3840, 2160))
        assertEquals(2, uploadSampleSize(6000, 4000))
        assertEquals(2, uploadSampleSize(4000, 6000))
        assertEquals(16, uploadSampleSize(1000, 40960))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() { uploadSampleSize(100, 0) }
}
