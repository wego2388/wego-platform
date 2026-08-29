package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.content.packageInquiryUrl
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.shared.catalog.DiveCatalog
import com.wego.mobile.shared.catalog.formatEur
import com.wego.mobile.shared.locale.AppLocale

/**
 * Mirrors `/package-builder` on the website: pick from the same real,
 * approved offerings, see a real running EUR total, send the exact list on
 * WhatsApp. No in-app checkout — matches the app's existing
 * inquiry-not-booking pattern used on every other screen.
 */
@Composable
@Suppress("FunctionName")
fun PackageBuilderScreen(
    locale: AppLocale,
    onOpenUrl: (String) -> Unit,
) {
    var selectedCodes by remember { mutableStateOf(setOf<String>()) }
    val selectedOfferings = DiveCatalog.offerings.filter { it.code in selectedCodes }
    val total = selectedOfferings.sumOf { it.priceEur }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(SiteCopy.PackageBuilder.heading.of(locale), style = MaterialTheme.typography.titleLarge)
            Text(SiteCopy.PackageBuilder.body.of(locale), style = MaterialTheme.typography.bodyMedium)
        }

        if (selectedOfferings.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(SdcSpace.xs),
            ) {
                Text(SiteCopy.PackageBuilder.totalLabel.of(locale), style = MaterialTheme.typography.labelMedium)
                Text(formatEur(total), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Button(
                    onClick = { onOpenUrl(packageInquiryUrl(selectedOfferings, total, locale)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(SiteCopy.PackageBuilder.whatsappCta.of(locale))
                }
            }
            HorizontalDivider()
        } else {
            Text(
                SiteCopy.PackageBuilder.emptyLabel.of(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(SdcSpace.sm),
        ) {
            items(DiveCatalog.offerings, key = { it.code }) { offering ->
                val selected = offering.code in selectedCodes
                SdcCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(SdcSpace.md + SdcSpace.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(offering.name.of(locale), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatEur(offering.priceEur),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val toggle = {
                            selectedCodes = if (selected) selectedCodes - offering.code else selectedCodes + offering.code
                        }
                        if (selected) {
                            TextButton(onClick = toggle) { Text(SiteCopy.PackageBuilder.removeLabel.of(locale)) }
                        } else {
                            OutlinedButton(onClick = toggle) { Text(SiteCopy.PackageBuilder.addLabel.of(locale)) }
                        }
                    }
                }
            }
        }
    }
}
