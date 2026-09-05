package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.customer.theme.SdcExtendedColors
import com.wego.mobile.shared.locale.AppLocale

/** Mirrors `contact.vue` — every channel here is `status: approved` in approved-facts.json. */
@Composable
@Suppress("FunctionName")
fun ContactScreen(
    locale: AppLocale,
    onOpenWhatsApp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SdcSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(SdcSpace.lg),
    ) {
        Text(SiteCopy.Contact.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.Contact.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        SdcCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primary, onClick = onOpenWhatsApp) {
            Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                Text(
                    SiteCopy.Contact.whatsappLabel.of(locale),
                    style = MaterialTheme.typography.labelSmall,
                    // onPrimary, not tertiary: tertiary-on-primary is a cross-role
                    // pairing no test verified — measures ~2.2:1 in both schemes,
                    // far under 4.5:1. onPrimary matches the two texts below it
                    // in the same card, already contrast-safe (see SdcThemeContrastTest).
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
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
    SdcCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(SdcSpace.md + SdcSpace.xs)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SdcExtendedColors.accentText)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
