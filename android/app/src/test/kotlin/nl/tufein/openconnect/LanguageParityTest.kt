package nl.tufein.openconnect

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.57.0 (milestone M4): the app is Dutch and English, and no text may exist in one language only, carry different
 * placeholders in the other, or be left in English in the Dutch file. Plain JUnit on the resource files; Gradle runs
 * unit tests in `android/app`, so they are read by relative path.
 */
class LanguageParityTest {
    private class Entry(val kind: String, val name: String, val text: String, val formatted: Boolean = true)

    private fun read(path: String): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out["string:$name"] = Entry("string", name, node.textContent, node.getAttribute("formatted") != "false")
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.sorted()
                    val text = (0 until items.length).joinToString("|") { items.item(it).textContent }
                    out["plurals:$name"] = Entry("plurals:" + quantities.joinToString(","), name, text)
                }
                "string-array" -> {
                    val items = node.getElementsByTagName("item")
                    out["array:$name"] = Entry("array:${items.length}", name, (0 until items.length).joinToString("|") { items.item(it).textContent })
                }
            }
        }
        return out
    }

    private val english get() = read("src/main/res/values/strings.xml")
    private val dutch get() = read("src/main/res/values-nl/strings.xml")

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")
    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    @Test
    fun `every text exists in both languages`() {
        val en = english.keys
        val nl = dutch.keys
        assertTrue(en.size > 500, "the English texts were found")
        assertEquals(emptyList(), (en - nl).sorted(), "in English but missing in Dutch")
        assertEquals(emptyList(), (nl - en).sorted(), "in Dutch but missing in English")
    }

    @Test
    fun `a text has the same placeholders in both languages`() {
        val en = english
        val nl = dutch
        // formatted="false" marks a text that is never passed to String.format, so a "%" in it is just a percent sign.
        val wrong = en.keys.intersect(nl.keys)
            .filter { en.getValue(it).formatted && nl.getValue(it).formatted }
            .filter { placeholders(en.getValue(it).text) != placeholders(nl.getValue(it).text) }
        assertEquals(emptyList(), wrong.sorted(), "placeholders differ between English and Dutch")
    }

    @Test
    fun `plurals and lists have the same shape in both languages`() {
        val en = english
        val nl = dutch
        val wrong = en.keys.intersect(nl.keys).filter { en.getValue(it).kind != nl.getValue(it).kind }
        assertEquals(emptyList(), wrong.sorted(), "plural quantities or list lengths differ")
    }

    @Test
    fun `no text is empty`() {
        val empty = (english.entries + dutch.entries).filter { it.value.text.isBlank() }.map { it.key }
        assertEquals(emptyList(), empty.sorted(), "empty texts")
    }

    /**
     * A Dutch text that is word for word the English one is almost always a forgotten translation. Names, units, number
     * formats and short labels that are the same in both languages are fine: only texts with four real words or more
     * (placeholders, numbers and punctuation do not count) are checked.
     */
    @Test
    fun `no long text was left in English in the Dutch file`() {
        val en = english
        val nl = dutch
        val left = en.keys.intersect(nl.keys).filter { key ->
            val text = nl.getValue(key).text.trim()
            text == en.getValue(key).text.trim() && words(text) >= 4
        }
        assertEquals(emptyList(), left.sorted(), "Dutch texts that are identical to the English ones")
    }

    private fun words(text: String) = Regex("""\p{L}{2,}""").findAll(placeholder.replace(text, " ")).count()
}
