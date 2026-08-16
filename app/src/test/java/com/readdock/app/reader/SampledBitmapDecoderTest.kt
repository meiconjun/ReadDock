package com.readdock.app.reader

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SampledBitmapDecoderTest {
    @Test
    fun `decodes oversized local bitmap with sampling`() {
        val directory = createTempDir(prefix = "reader-decode-test-")
        val source = File(directory, "large.png")
        val bitmap = Bitmap.createBitmap(2200, 3300, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(source).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 90, output))
            }
            val decoded = SampledBitmapDecoder().decodeFile("large", source)
            assertTrue(decoded.width <= 2048)
            assertTrue(decoded.height <= 3072)
        } finally {
            bitmap.recycle()
            directory.deleteRecursively()
        }
    }
}
