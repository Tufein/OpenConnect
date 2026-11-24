package nl.tufein.openconnect

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural checks on the translated resources: every language has the same keys, no text is empty, the placeholders
 * match, lists keep their length and plurals keep their quantities (French may add its CLDR "many" form).
 *
 * These are checks on the files, not a review of the language. Whether a German or French sentence reads naturally is
 * for a native speaker to say; this test only proves nothing is missing or broken. The base `values/strings.xml` is
 * English, and every `values-<lang>/strings.xml` under src/main/res is discovered automatically, so a translation that
 * is added later is checked without touching this file.
 */
class LanguageParityTest {
    private enum class Kind { STRING, ARRAY, PLURALS }

    private class Entry(
        val kind: Kind,
        /** For plurals, the Android quantities in the same order as [texts]; empty otherwise. */
        val quantities: List<String> = emptyList(),
        val texts: List<String>,
        /** `formatted="false"` marks a text that is never passed to String.format, so "%" in it is just a percent sign. */
        val formatted: Boolean = true,
    )

    private val res = File("src/main/res")
    private val base = "en"

    /** The languages with texts: the base `values/` is English, `values-nl/`, `values-de/` and `values-fr/` the rest. */
    private fun languages(): Map<String, File> {
        val found = linkedMapOf(base to File(res, "$BASE_DIR/strings.xml"))
        res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.isFile }?.let { dir.name to it } }
            .sortedBy { it.first }
            .forEach { (dir, file) -> found[dir.removePrefix("values-")] = file }
        return found
    }

    private fun read(path: File): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path)
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val name = node.getAttribute("name")
            val formatted = node.getAttribute("formatted") != "false"
            when (node.tagName) {
                "string" -> out["string:$name"] = Entry(Kind.STRING, texts = listOf(node.textContent), formatted = formatted)
                "string-array" -> {
                    val items = node.getElementsByTagName("item")
                    val texts = (0 until items.length).map { items.item(it).textContent }
                    out["array:$name"] = Entry(Kind.ARRAY, texts = texts, formatted = formatted)
                }
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.sorted()
                    val byQuantity = (0 until items.length).associate {
                        (items.item(it) as Element).getAttribute("quantity") to items.item(it).textContent
                    }
                    out["plurals:$name"] = Entry(
                        Kind.PLURALS,
                        quantities = quantities,
                        texts = quantities.map { byQuantity.getValue(it) },
                        formatted = formatted,
                    )
                }
            }
        }
        return out
    }

    private val english get() = read(File(res, "$BASE_DIR/strings.xml"))

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

    private fun placeholders(text: String) =
        placeholder.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    @Test
    fun `the English base is there and every supported language has its own texts`() {
        val found = languages().keys
        assertTrue(found.contains(base), "the English base values/strings.xml was found")
        assertEquals(emptyList(), (setOf("en", "nl", "de", "fr") - found).sorted(), "languages without strings.xml")
        assertEquals(emptyList(), (found - setOf("en", "nl", "de", "fr")).sorted(), "translations the app does not offer")
        assertTrue(english.size > 500, "the English texts were read (${english.size})")
    }

    @Test
    fun `every language has exactly the English keys and the same kind of entry`() {
        val en = english
        for ((language, file) in languages()) {
            if (language == base) continue
            val other = read(file)
            assertEquals(emptyList(), (en.keys - other.keys).sorted(), "in English but missing in $language")
            assertEquals(emptyList(), (other.keys - en.keys).sorted(), "in $language but missing in English")
            val wrongKind = en.keys.intersect(other.keys)
                .filter { en.getValue(it).kind != other.getValue(it).kind }
            assertEquals(emptyList(), wrongKind.sorted(), "a string, list or plural changed kind in $language")
        }
    }

    @Test
    fun `a text has the same placeholders in every language`() {
        val en = english
        for ((language, file) in languages()) {
            if (language == base) continue
            val other = read(file)
            val wrong = mutableListOf<String>()
            for (key in en.keys.intersect(other.keys)) {
                val baseEntry = en.getValue(key)
                val target = other.getValue(key)
                if (!baseEntry.formatted || !target.formatted) continue
                when (baseEntry.kind) {
                    Kind.STRING ->
                        if (placeholders(baseEntry.texts[0]) != placeholders(target.texts[0])) wrong += key
                    Kind.ARRAY ->
                        for (i in baseEntry.texts.indices) {
                            val text = target.texts.getOrNull(i) ?: continue
                            if (placeholders(baseEntry.texts[i]) != placeholders(text)) wrong += "$key[$i]"
                        }
                    Kind.PLURALS ->
                        for (i in baseEntry.quantities.indices) {
                            val quantity = baseEntry.quantities[i]
                            val j = target.quantities.indexOf(quantity)
                            if (j < 0) continue // reported by the plural test
                            if (placeholders(baseEntry.texts[i]) != placeholders(target.texts[j])) wrong += "$key[$quantity]"
                        }
                }
            }
            assertEquals(emptyList(), wrong.sorted(), "placeholders differ in $language")
        }
    }

    @Test
    fun `lists have the same number of items in every language`() {
        val en = english
        for ((language, file) in languages()) {
            if (language == base) continue
            val other = read(file)
            val wrong = en.keys.intersect(other.keys)
                .filter { en.getValue(it).kind == Kind.ARRAY && other.getValue(it).kind == Kind.ARRAY }
                .filter { en.getValue(it).texts.size != other.getValue(it).texts.size }
            assertEquals(emptyList(), wrong.sorted(), "a list changed length in $language")
        }
    }

    @Test
    fun `plurals keep every English quantity and only French may add many`() {
        val en = english
        for ((language, file) in languages()) {
            if (language == base) continue
            val other = read(file)
            for (key in en.keys.intersect(other.keys)) {
                val baseEntry = en.getValue(key)
                val target = other.getValue(key)
                if (baseEntry.kind != Kind.PLURALS || target.kind != Kind.PLURALS) continue
                assertEquals(
                    emptyList(),
                    (baseEntry.quantities - target.quantities.toSet()).sorted(),
                    "$language is missing plural quantities for $key",
                )
                // French counts a "many" form (for a million and above); English, Dutch and German have no other extra.
                val allowed = if (language == "fr") setOf("many") else emptySet()
                assertEquals(
                    emptyList(),
                    (target.quantities - baseEntry.quantities.toSet() - allowed).sorted(),
                    "$language has plural quantities English does not have for $key",
                )
            }
        }
    }

    @Test
    fun `no text is empty`() {
        for ((language, file) in languages()) {
            val empty = read(file).filterValues { entry -> entry.texts.any { it.isBlank() } }.keys
            assertEquals(emptyList(), empty.sorted(), "empty texts in $language")
        }
    }

    private companion object {
        const val BASE_DIR = "values"
    }
}
