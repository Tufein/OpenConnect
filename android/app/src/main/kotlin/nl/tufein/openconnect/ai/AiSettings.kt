package nl.tufein.openconnect.ai

import nl.tufein.openconnect.core.reports.AiVendor

/**
 * What the app remembers about the AI providers. The keys are kept encrypted by the real implementation, and a phone
 * that cannot do that keeps none ([canStoreKeys] is false), never a key in plain text.
 */
interface AiSettings {
    val canStoreKeys: Boolean

    /** "auto", "template", or a vendor id ("anthropic", "gemini"). */
    var choice: String

    /** The language reports and coach answers are written in: "en" or "nl". */
    var language: String

    fun key(vendor: AiVendor): String?

    fun setKey(vendor: AiVendor, key: String?)

    /** The model the person picked, or null for the vendor's default. */
    fun model(vendor: AiVendor): String?

    fun setModel(vendor: AiVendor, model: String?)
}
