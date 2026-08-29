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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcBadge
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcColor
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
                    color = SdcColor.deepBright,
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
            Text(
                SiteCopy.Discover.heading.of(locale),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = SdcSpace.sm),
            )
        }

        items(Categories.all) { category ->
            SdcCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = SdcColor.turquoiseSoft,
                onClick = { onCategoryClick(category.id) },
            ) {
                Column(modifier = Modifier.padding(SdcSpace.lg), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                    Text(
                        category.eyebrow.of(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = SdcColor.deepBright,
                    )
                    Text(category.title.of(locale), style = MaterialTheme.typography.titleMedium, color = SdcColor.textPrimary)
                    Text(category.description.of(locale), style = MaterialTheme.typography.bodyMedium, color = SdcColor.textSecondary)
                }
            }
        }

        item {
            Text(
                SiteCopy.Discover.pricingNotice.of(locale),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = SdcSpace.xs, bottom = SdcSpace.xxl),
            )
        }
    }
}
