package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.shared.catalog.DiveSite
import com.wego.mobile.shared.catalog.DiveSites
import com.wego.mobile.shared.catalog.offerings
import com.wego.mobile.shared.locale.AppLocale

/**
 * Mirrors `/dive-sites` on the website: the same 4 real named sites, each
 * linked only to the real offerings that visit it. No live sea/weather
 * widget here — that needs a real cross-platform HTTP client this codebase
 * doesn't have yet, and this box has no way to verify an iOS network path,
 * so it's deliberately left for a future phase rather than shipped unverified.
 */
@Composable
@Suppress("FunctionName")
fun DiveSitesScreen(
    locale: AppLocale,
    onSiteClick: (DiveSite) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(SiteCopy.DiveSites.heading.of(locale), style = MaterialTheme.typography.titleLarge)
            Text(SiteCopy.DiveSites.body.of(locale), style = MaterialTheme.typography.bodyMedium)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(SdcSpace.md),
        ) {
            items(DiveSites.all, key = { it.slug }) { site ->
                SdcCard(modifier = Modifier.fillMaxWidth(), onClick = { onSiteClick(site) }) {
                    Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                        Text(site.name.of(locale), style = MaterialTheme.typography.titleMedium)
                        Text(
                            site.blurb.of(locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${site.offerings().size} · ${SiteCopy.DiveSites.offeringsHeading.of(locale)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
