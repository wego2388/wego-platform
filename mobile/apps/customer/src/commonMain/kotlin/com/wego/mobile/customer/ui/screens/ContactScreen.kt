package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.shared.locale.AppLocale

/** Mirrors `contact.vue` — every channel here is `status: approved` in approved-facts.json. */
@Composable
@Suppress("FunctionName")
fun ContactScreen(
    locale: AppLocale,
    onOpenWhatsApp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(SiteCopy.Contact.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.Contact.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = onOpenWhatsApp,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    SiteCopy.Contact.whatsappLabel.of(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    SiteCopy.PHONE_DISPLAY,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    SiteCopy.Contact.whatsappBody.of(locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        ContactFact(SiteCopy.Contact.phoneLabel.of(locale), SiteCopy.PHONE_DISPLAY)
        ContactFact(SiteCopy.Contact.emailLabel.of(locale), SiteCopy.EMAIL)
        ContactFact(SiteCopy.Contact.locationLabel.of(locale), SiteCopy.location.of(locale))
        ContactFact(SiteCopy.Contact.hoursLabel.of(locale), SiteCopy.hours.of(locale))
    }
}

@Composable
@Suppress("FunctionName")
private fun ContactFact(
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
