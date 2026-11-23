package com.anamorsyai.alpai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Role

/**
 * Very small markdown-ish renderer for v0.1: **bold**, *italic* and
 * `inline code`. Full Markdown (lists, code blocks, tables) is a later
 * step — this keeps streaming cheap and dependency-free.
 */
@Composable
fun rememberMarkdown(text: String): AnnotatedString {
    return remember(text) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("**", i) -> {
                        val end = text.indexOf("**", i + 2)
                        if (end == -1) {
                            append(text.substring(i)); i = text.length
                        } else {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 2
                        }
                    }
                    text.startsWith("`", i) -> {
                        val end = text.indexOf("`", i + 1)
                        if (end == -1) {
                            append(text.substring(i)); i = text.length
                        } else {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = androidx.compose.ui.graphics.Color(0x33000000),
                                ),
                            ) { append(text.substring(i + 1, end)) }
                            i = end + 1
                        }
                    }
                    text.startsWith("*", i) -> {
                        val end = text.indexOf("*", i + 1)
                        if (end == -1) {
                            append(text.substring(i)); i = text.length
                        } else {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        }
                    }
                    else -> {
                        append(text[i]); i++
                    }
                }
            }
        }
    }
}

/** One chat bubble. User = right, primary color. AI = left, surface. */
@Composable
fun MessageBubble(message: ChatMessage, fontScale: Float = 1f) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.attachments.isNotEmpty()) {
                    Text(
                        text = message.attachments.joinToString(", ") { "📎 ${it.name}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = rememberMarkdown(message.content),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                    ),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (message.isStreaming) {
                    Text(
                        text = "▍",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Small label used above lists / sections. */
@Composable
fun SectionLabel(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}
