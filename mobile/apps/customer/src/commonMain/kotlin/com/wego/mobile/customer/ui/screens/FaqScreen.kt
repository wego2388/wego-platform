package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcColor
import com.wego.mobile.customer.design.SdcSpace
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SdcSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(SdcSpace.lg),
    ) {
        Text(SiteCopy.Faq.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.Faq.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        Text(
            SiteCopy.Faq.knownHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SdcSpace.sm),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm)) {
            for ((question, answer) in SiteCopy.Faq.known) {
                FaqCard(question.of(locale), answer.of(locale), SdcColor.turquoiseSoft)
            }
        }

        Text(
            SiteCopy.Faq.unknownHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SdcSpace.sm),
        )
        Text(SiteCopy.Faq.unknownIntro.of(locale), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm)) {
            for ((question, answer) in SiteCopy.Faq.unknown) {
                FaqCard(question.of(locale), answer.of(locale), SdcColor.sandSoft)
            }
        }

        Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth().padding(top = SdcSpace.sm, bottom = SdcSpace.xxl)) {
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
    SdcCard(modifier = Modifier.fillMaxWidth(), containerColor = containerColor) {
        Column(modifier = Modifier.padding(SdcSpace.md + SdcSpace.xs), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
            Text(question, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
