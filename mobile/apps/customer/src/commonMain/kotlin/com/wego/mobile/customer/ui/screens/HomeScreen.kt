package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcBadge
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcCategoryIcon
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.shared.catalog.Categories
import com.wego.mobile.shared.catalog.CategoryId
import com.wego.mobile.shared.locale.AppLocale

@Composable
@Suppress("FunctionName")
fun HomeScreen(
    locale: AppLocale,
    onBrowseAll: () -> Unit,
    onCategoryClick: (CategoryId) -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenTripAdvisor: () -> Unit,
    onOpenDiveSites: () -> Unit,
    onOpenPackageBuilder: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SdcSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(SdcSpace.xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.md)) {
                Text(
                    SiteCopy.Hero.eyebrow.of(locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(SiteCopy.Hero.title.of(locale), style = MaterialTheme.typography.headlineMedium)
                Text(SiteCopy.Hero.body.of(locale), style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(SdcSpace.md)) {
                Button(onClick = onBrowseAll) { Text(SiteCopy.Hero.browseCta.of(locale)) }
                OutlinedButton(onClick = onOpenWhatsApp) { Text(SiteCopy.Hero.whatsappCta.of(locale)) }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SdcSpace.sm), verticalArrangement = Arrangement.spacedBy(SdcSpace.sm)) {
                for (guarantee in SiteCopy.guarantees) {
                    SdcBadge(guarantee.of(locale))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBlock(SiteCopy.Stats.CATEGORIES_VALUE, SiteCopy.Stats.categoriesLabel.of(locale))
                StatBlock(SiteCopy.Stats.LANGUAGES_VALUE, SiteCopy.Stats.languagesLabel.of(locale))
                StatBlock(SiteCopy.Stats.PADI_VALUE, SiteCopy.Stats.padiLabel.of(locale))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(SdcSpace.md)) {
                OutlinedButton(onClick = onOpenDiveSites) { Text(SiteCopy.DiveSites.navLabel.of(locale)) }
                OutlinedButton(onClick = onOpenPackageBuilder) { Text(SiteCopy.PackageBuilder.navLabel.of(locale)) }
            }
        }

        item {
            Text(
                SiteCopy.Discover.heading.of(locale),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = SdcSpace.sm),
            )
        }

        items(Categories.all) { category ->
            SdcCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onCategoryClick(category.id) },
            ) {
                Row(
                    modifier = Modifier.padding(SdcSpace.lg),
                    horizontalArrangement = Arrangement.spacedBy(SdcSpace.md),
                ) {
                    SdcCategoryIcon(category.id, tint = MaterialTheme.colorScheme.primary, iconSize = 28.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                        Text(
                            category.eyebrow.of(locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(category.title.of(locale), style = MaterialTheme.typography.titleMedium)
                        Text(
                            category.description.of(locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                SiteCopy.Discover.pricingNotice.of(locale),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = SdcSpace.xs),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm), modifier = Modifier.padding(top = SdcSpace.md)) {
                Text(SiteCopy.How.heading.of(locale), style = MaterialTheme.typography.titleLarge)
                Text(SiteCopy.How.body.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(SiteCopy.How.steps) { (title, body) ->
            SdcCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                    Text(title.of(locale), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(body.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm), modifier = Modifier.padding(top = SdcSpace.md)) {
                Text(SiteCopy.Personas.heading.of(locale), style = MaterialTheme.typography.titleLarge)
                Text(SiteCopy.Personas.body.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(SiteCopy.Personas.items) { (name, body) ->
            SdcCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                    Text(name.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(body.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm), modifier = Modifier.padding(top = SdcSpace.md)) {
                Text(SiteCopy.Why.heading.of(locale), style = MaterialTheme.typography.titleLarge)
                Text(SiteCopy.Why.body.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(SiteCopy.Why.items) { (title, body) ->
            SdcCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                    Text(title.of(locale), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(body.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            TextButton(
                onClick = onOpenTripAdvisor,
                modifier = Modifier.fillMaxWidth().padding(top = SdcSpace.sm, bottom = SdcSpace.xxl),
            ) {
                Text(SiteCopy.tripadvisorLabel.of(locale))
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun StatBlock(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
