package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** `**bold**` spans only; everything else is shown literally, so report text can never inject UI. */
internal fun inlineMarkdown(line: String): AnnotatedString = buildAnnotatedString {
    var rest = line
    while (true) {
        val open = rest.indexOf("**")
        val close = if (open >= 0) rest.indexOf("**", open + 2) else -1
        if (open < 0 || close < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, open))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(rest.substring(open + 2, close))
        pop()
        rest = rest.substring(close + 2)
    }
}

/** A deliberately tiny Markdown view: headings, bullets and bold, which is all the reports use. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> Text(
                    inlineMarkdown(line.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                line.startsWith("- ") || line.startsWith("* ") ->
                    Text(inlineMarkdown("•  " + line.drop(2)), style = MaterialTheme.typography.bodyMedium)
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
