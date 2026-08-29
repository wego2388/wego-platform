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
import com.wego.mobile.shared.locale.AppLocale

/** Mirrors `about.vue` — every fact here is `status: approved` in approved-facts.json. */
@Composable
@Suppress("FunctionName")
fun AboutScreen(locale: AppLocale) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SdcSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(SdcSpace.lg),
    ) {
        Text(SiteCopy.About.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.About.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        Text(
            SiteCopy.About.factsHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SdcSpace.sm),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm)) {
            for ((label, value) in SiteCopy.About.facts) {
                SdcCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(SdcSpace.md + SdcSpace.xs)) {
                        Text(
                            label.of(locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(value.of(locale), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text(
            SiteCopy.About.languagesHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SdcSpace.sm),
        )
        Text(SiteCopy.About.languagesBody.of(locale), style = MaterialTheme.typography.bodyMedium)
    }
}
