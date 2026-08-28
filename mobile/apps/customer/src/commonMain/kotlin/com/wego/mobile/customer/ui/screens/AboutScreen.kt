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

/** Mirrors `about.vue` — every fact here is `status: approved` in approved-facts.json. */
@Composable
@Suppress("FunctionName")
fun AboutScreen(locale: AppLocale) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(SiteCopy.About.heading.of(locale), style = MaterialTheme.typography.headlineSmall)
        Text(SiteCopy.About.body.of(locale), style = MaterialTheme.typography.bodyLarge)

        Text(
            SiteCopy.About.factsHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in SiteCopy.About.facts) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            label.of(locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(value.of(locale), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text(
            SiteCopy.About.languagesHeading.of(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(SiteCopy.About.languagesBody.of(locale), style = MaterialTheme.typography.bodyMedium)
    }
}
