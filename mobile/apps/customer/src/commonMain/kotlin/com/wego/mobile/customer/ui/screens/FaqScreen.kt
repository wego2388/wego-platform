package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.shared.locale.AppLocale

/**
 * The same two-tier honesty pattern as `faq.vue`: real answers we can give
 * right now, and an explicit "confirmed on WhatsApp" list for anything still
 * gated by an open governance decision — never a guessed answer.
 */
@Composable
@Suppress("FunctionName")
fun FaqScreen(
    locale: AppLocale,
    onOpenWhatsApp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(SiteCopy.Faq.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.Faq.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        Text(
            SiteCopy.Faq.knownHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((question, answer) in SiteCopy.Faq.known) {
                FaqCard(question.of(locale), answer.of(locale), MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        Text(
            SiteCopy.Faq.unknownHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(SiteCopy.Faq.unknownIntro.of(locale), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((question, answer) in SiteCopy.Faq.unknown) {
                FaqCard(question.of(locale), answer.of(locale), MaterialTheme.colorScheme.errorContainer)
            }
        }

        Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
            Text(SiteCopy.Faq.whatsappCta.of(locale))
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun FaqCard(
    question: String,
    answer: String,
    containerColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
