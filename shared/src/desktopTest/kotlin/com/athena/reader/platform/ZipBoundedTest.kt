package com.athena.reader.platform

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class ZipBoundedTest {

    @Test
    fun `path-traversal entries never appear in the map`() {
        val bytes = zipOf(
            "OEBPS/ok.xhtml" to "<p>ok</p>",
            "../secret" to "nope",
            "/abs" to "nope",
        )
        val entries = readZipEntriesBounded(bytes)
        assertTrue("OEBPS/ok.xhtml" in entries)
        assertTrue(entries.keys.none { "secret" in it || it.startsWith("/abs") })
    }

    @Test
    fun `an oversized entry is dropped instead of inflating`() {
        val huge = "x".repeat(Limits.MAX_ZIP_ENTRY_BYTES + 50)
        val bytes = zipOf("OEBPS/big.xhtml" to huge, "OEBPS/small.xhtml" to "<p>hi</p>")
        val entries = readZipEntriesBounded(bytes)
        assertTrue("OEBPS/big.xhtml" !in entries)
        assertTrue("OEBPS/small.xhtml" in entries)
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
