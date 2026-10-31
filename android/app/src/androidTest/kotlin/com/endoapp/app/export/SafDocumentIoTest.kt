package com.endoapp.app.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-gated proof of [SafDocumentIo]'s `"wt"` truncate behavior on a REAL `ContentResolver`
 * (APRT-01, threat T-23-07) — the cross-provider stale-tail guard the pure-JVM lane cannot exercise.
 *
 * Writes a LONG byte array, then a SHORTER one, to the SAME content `Uri` through [SafDocumentIo], and
 * asserts the read-back equals the SHORTER bytes — i.e. `openOutputStream(uri, "wt")` truncated the
 * longer prior content and left NO stale tail. Without the `t` in the mode this would fail (the old
 * file's trailing bytes would survive past the new content).
 *
 * Compiles in the androidTest lane on the planning host (no emulator there — mirrors the
 * [com.endoapp.app.data.DeleteDayCascadeTest] precedent); the actual run is the CI/device lane
 * (`connectedDebugAndroidTest --tests "*SafDocumentIoTest"`), surfaced to /gsd-verify-work.
 */
@RunWith(AndroidJUnit4::class)
class SafDocumentIoTest {

    private lateinit var io: SafDocumentIo
    private lateinit var file: File
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        io = SafDocumentIo(context.contentResolver)
        // A real file the ContentResolver can open by URI — the "wt" truncate is a resolver behavior,
        // exercised here without needing an interactive SAF document picker.
        file = File.createTempFile("endo-saf-truncate", ".enc", context.cacheDir)
        uri = Uri.fromFile(file)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun writeShorterOverLonger_leavesNoStaleTail() = runBlocking {
        val longBytes = ByteArray(4_096) { (it % 251).toByte() }
        val shortBytes = ByteArray(64) { (255 - (it % 251)).toByte() }

        io.write(uri, longBytes)
        assertEquals("Long write should land in full.", longBytes.size.toLong(), file.length())

        io.write(uri, shortBytes)

        val readBack = io.read(uri)
        assertEquals(
            "\"wt\" must truncate — the shorter write must leave a file of exactly the short length " +
                "(no stale tail from the longer prior content).",
            shortBytes.size,
            readBack.size,
        )
        assertArrayEquals(
            "The read-back must equal the shorter bytes exactly — no stale trailing bytes.",
            shortBytes,
            readBack,
        )
    }
}
