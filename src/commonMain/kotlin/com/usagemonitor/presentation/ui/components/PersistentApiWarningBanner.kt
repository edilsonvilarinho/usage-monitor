package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes

enum class BannerTone {
    INFO,
    ERROR
}

@Composable
fun PersistentApiWarningBanner(
    title: String,
    description: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    tone: BannerTone = BannerTone.ERROR,
    modifier: Modifier = Modifier
) {
    val containerColor = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.80f)
    }
    val accentColor = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primary
        BannerTone.ERROR -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        tonalElevation = AppElevation.banner,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tone == BannerTone.INFO) "i" else "!",
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f)
            )

            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
