package com.wovenledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.wovenledger.app.ui.theme.WovenDanger
import com.wovenledger.app.ui.theme.WovenSuccess
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDIA = Locale("en", "IN")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", INDIA)

/**
 * Money is stored as integer paise everywhere in this app. Formatting is centralised
 * here so a stray `/ 100` in a screen can never silently render rupees as paise.
 */
fun formatMoney(paise: Long): String {
    val format = NumberFormat.getCurrencyInstance(INDIA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    return format.format(paise / 100.0)
}

fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

/**
 * Which side of the ledger a figure sits on. Money owed to us and money we owe read
 * very differently at a glance, so they are coloured rather than distinguished only
 * by a caption.
 */
enum class MoneyTone { Neutral, Receive, Pay }

@Composable
fun moneyToneColor(tone: MoneyTone): Color = when (tone) {
    MoneyTone.Neutral -> MaterialTheme.colorScheme.onSurface
    MoneyTone.Receive -> WovenSuccess
    MoneyTone.Pay -> WovenDanger
}

/** Money and document numbers use a monospace face so columns of figures line up. */
@Composable
fun MoneyText(
    paise: Long,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    tone: MoneyTone = MoneyTone.Neutral,
) {
    Text(
        text = formatMoney(paise),
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium,
        style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = moneyToneColor(tone),
    )
}

@Composable
fun DocumentNumberText(number: String, modifier: Modifier = Modifier) {
    Text(
        text = number,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
) {
    CenteredMessage(
        icon = icon,
        title = message,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        icon = Icons.Filled.CloudOff,
        title = message,
        modifier = modifier,
        action = {
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Try again")
            }
        },
    )
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** Shown above a list when the cached data on screen could not be refreshed. */
@Composable
fun SyncBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
