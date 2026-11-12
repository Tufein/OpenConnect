package nl.tufein.openconnect.core.formats

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private val FORBIDDEN = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)

/**
 * Parse untrusted XML. Documents with a DOCTYPE or entity declarations are refused outright, which
 * rules out external entity (XXE) and entity expansion ("billion laughs") attacks on every platform.
 */
internal fun parseXml(data: ByteArray, what: String): Element {
    // Dropping NUL bytes lets the same check catch UTF-16 encoded documents.
    val probe = String(data.filter { it != 0.toByte() }.toByteArray(), Charsets.ISO_8859_1)
    if (FORBIDDEN.containsMatchIn(probe)) {
        throw FormatError("Not a valid $what file: DOCTYPE and entity declarations are not allowed")
    }
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    // Belt and braces; not every XML implementation (for example Android's) knows every feature.
    for ((feature, value) in listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
    )) {
        try {
            factory.setFeature(feature, value)
        } catch (_: ParserConfigurationException) {
        } catch (_: UnsupportedOperationException) {
        }
    }
    try {
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
    } catch (_: UnsupportedOperationException) {
    }
    return try {
        factory.newDocumentBuilder().parse(ByteArrayInputStream(data)).documentElement
    } catch (e: SAXException) {
        throw FormatError("Not a valid $what file: ${e.message}", e)
    } catch (e: IOException) {
        throw FormatError("Not a valid $what file: ${e.message}", e)
    }
}

internal val Node.local: String get() = localName ?: nodeName.substringAfter(':')

internal fun Element.elements(): List<Element> {
    val out = ArrayList<Element>()
    var node = firstChild
    while (node != null) {
        if (node is Element) out.add(node)
        node = node.nextSibling
    }
    return out
}

/** This element and every element below it, in document order. */
internal fun Element.descendantsAndSelf(): Sequence<Element> = sequence {
    yield(this@descendantsAndSelf)
    for (child in elements()) yieldAll(child.descendantsAndSelf())
}

internal fun Element.child(name: String): Element? = elements().firstOrNull { it.local == name }

internal fun Element.childText(name: String): String? =
    elements().firstOrNull { it.local == name && !it.textContent.isNullOrBlank() }?.textContent?.trim()

internal fun Element.descendantText(name: String): String? =
    descendantsAndSelf().firstOrNull { it.local == name && !it.textContent.isNullOrBlank() }?.textContent?.trim()

internal fun Element.path(vararg names: String): Element? {
    var current: Element? = this
    for (name in names) current = current?.child(name) ?: return null
    return current
}

internal fun xmlEscape(text: String): String = buildString(text.length + 8) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
