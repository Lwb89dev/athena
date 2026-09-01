package com.athena.reader.data.importer

import com.athena.reader.platform.Log
import com.athena.reader.platform.readZipEntries

/**
 * EPUB 2 and 3.
 *
 * An EPUB is a zip holding XHTML chapters plus an OPF manifest that gives the
 * metadata and, crucially, the *reading order* — the spine. Following the spine
 * rather than sorting file names is the difference between a book and a pile of
 * chapters, because publishers name files anything.
 *
 * The OPF is read with regexes rather than an XML parser: commonMain has none,
 * and pulling a multiplatform XML library in for four fields is a poor trade.
 * The cost is real and worth stating — an EPUB with unusual namespacing or
 * attributes in an unexpected order may fail to parse, and it will then fail
 * visibly rather than importing something wrong.
 */
class EpubImporter : BookImporter {

    override val extensions = setOf("epub")

    override fun import(fileName: String, bytes: ByteArray): ImportedBook? {
        val entries = runCatching { readZipEntries(bytes) }.getOrElse {
            Log.w(TAG, "not a readable zip: ${it.message}")
            return null
        }
        if (entries.isEmpty()) return null

        val opfPath = findOpfPath(entries) ?: return null
        val opf = entries[opfPath]?.decodeToString() ?: return null
        val basePath = opfPath.substringBeforeLast('/', "")

        val sections = readSpine(opf, entries, basePath)
        if (sections.isEmpty()) {
            Log.w(TAG, "EPUB parsed but no readable chapters were found")
            return null
        }

        return ImportedBook(
            title = opf.dublinCore("title") ?: fileName.substringBeforeLast('.'),
            author = opf.dublinCore("creator").orEmpty(),
            summary = opf.dublinCore("description")?.stripTags().orEmpty(),
            language = opf.dublinCore("language").orEmpty(),
            topics = Regex("<dc:subject[^>]*>(.*?)</dc:subject>", RegexOption.DOT_MATCHES_ALL)
                .findAll(opf)
                .map { it.groupValues[1].stripTags().trim() }
                .filter(String::isNotBlank)
                .toList(),
            sections = sections,
            sourceFileName = fileName,
        )
    }

    /** `META-INF/container.xml` names the OPF; falling back to a scan is fine. */
    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = entries["META-INF/container.xml"]?.decodeToString()
        val declared = container
            ?.let { Regex("""full-path\s*=\s*["']([^"']+)["']""").find(it) }
            ?.groupValues?.get(1)

        return declared?.takeIf { entries.containsKey(it) }
            ?: entries.keys.firstOrNull { it.endsWith(".opf") }
    }

    private fun readSpine(
        opf: String,
        entries: Map<String, ByteArray>,
        basePath: String,
    ): List<ImportedSection> {
        val manifest = Regex("""<item\b[^>]*>""")
            .findAll(opf)
            .mapNotNull { tag ->
                val id = tag.value.attribute("id") ?: return@mapNotNull null
                val href = tag.value.attribute("href") ?: return@mapNotNull null
                id to href
            }
            .toMap()

        val order = Regex("""<itemref\b[^>]*>""")
            .findAll(opf)
            .mapNotNull { it.value.attribute("idref") }
            .toList()

        // No spine at all: fall back to manifest order rather than giving up.
        val hrefs = if (order.isEmpty()) manifest.values.toList() else order.mapNotNull(manifest::get)

        return hrefs.mapIndexedNotNull { index, href ->
            val path = resolve(basePath, href)
            val html = entryBytes(entries, path)?.decodeToString() ?: return@mapIndexedNotNull null
            val text = html.stripTags()
            if (text.isBlank()) return@mapIndexedNotNull null

            ImportedSection(
                title = html.chapterTitle() ?: "Chapter ${index + 1}",
                content = text,
            )
        }
    }

    private fun resolve(basePath: String, href: String): String {
        val cleaned = percentDecode(href.substringBefore('#').replace('\\', '/'))
        return if (basePath.isEmpty()) cleaned else "$basePath/$cleaned"
    }

    /**
     * EPUB hrefs are URL-encoded and zip entry names are not; some also
     * disagree on case. Missing that pairing looks like "the book has no
     * chapters" rather than a path mismatch.
     */
    private fun entryBytes(entries: Map<String, ByteArray>, path: String): ByteArray? {
        val wanted = path.trimStart('/')
        entries[wanted]?.let { return it }
        entries[path]?.let { return it }
        val lower = wanted.lowercase()
        return entries.entries.firstOrNull { (key, _) ->
            key.replace('\\', '/').trimStart('/').lowercase() == lower
        }?.value
    }

    private fun percentDecode(path: String): String {
        val out = StringBuilder(path.length)
        var index = 0
        while (index < path.length) {
            val char = path[index]
            if (char == '%' && index + 2 < path.length) {
                val byte = path.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    out.append(byte.toChar())
                    index += 3
                    continue
                }
            }
            out.append(char)
            index++
        }
        return out.toString()
    }

    private fun String.attribute(name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(this)?.groupValues?.get(1)

    private fun String.dublinCore(field: String): String? =
        Regex("""<dc:$field[^>]*>(.*?)</dc:$field>""", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues?.get(1)
            ?.stripTags()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun String.chapterTitle(): String? =
        Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues?.get(1)
            ?.stripTags()
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 120 }

    /**
     * XHTML to readable text. Block-level tags become newlines first, otherwise
     * every paragraph would run into the next one.
     */
    private fun String.stripTags(): String = this
        .replace(Regex("""<(script|style)\b.*?</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</(p|div|h[1-6]|li|tr)\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<[^>]+>"""), "")
        .decodeEntities()
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun String.decodeEntities(): String = this
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")

    private companion object {
        const val TAG = "EpubImporter"
    }
}
