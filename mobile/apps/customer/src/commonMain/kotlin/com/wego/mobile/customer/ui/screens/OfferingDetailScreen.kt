package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.content.offeringInquiryUrl
import com.wego.mobile.shared.catalog.Categories
import com.wego.mobile.shared.catalog.DiveCatalog
import com.wego.mobile.shared.catalog.Offering
import com.wego.mobile.shared.catalog.diveCountLabel
import com.wego.mobile.shared.catalog.durationLabel
import com.wego.mobile.shared.catalog.formatEur
import com.wego.mobile.shared.catalog.label
import com.wego.mobile.shared.locale.AppLocale

/**
 * Mirrors `discover/[code].vue`: real price + meta, related offerings in the
 * same category, and a prefilled WhatsApp inquiry link — not an in-app
 * checkout, since `products/divers` has no public booking endpoint.
 */
@Composable
@Suppress("FunctionName")
fun OfferingDetailScreen(
    code: String,
    locale: AppLocale,
    onBack: () -> Unit,
    onOfferingClick: (Offering) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val offering = DiveCatalog.byCode(code)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onClick = onBack) { Text(SiteCopy.Discover.back.of(locale)) }

        if (offering == null) {
            Text(SiteCopy.Discover.notFound.of(locale), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        val category = Categories.byId(offering.categoryId)
        val related = DiveCatalog.byCategory(offering.categoryId).filter { it.code != offering.code }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 16.dp)) {
            Text(
                category.title.of(locale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(offering.name.of(locale), style = MaterialTheme.typography.headlineSmall)
            Text(offering.audience.label(locale), style = MaterialTheme.typography.bodyMedium)
        }

        val meta =
            listOfNotNull(
                durationLabel(locale, offering.durationMinutes),
                diveCountLabel(locale, offering.diveCount),
            ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(formatEur(offering.priceEur), style = MaterialTheme.typography.headlineMedium)
        Text(
            SiteCopy.Discover.pricingNotice.of(locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Button(
            onClick = { onOpenUrl(offeringInquiryUrl(offering, locale)) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text(SiteCopy.Discover.whatsappCta.of(locale))
        }

        if (related.isNotEmpty()) {
            Text(
                SiteCopy.Discover.moreInCategory.of(locale),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in related) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { onOfferingClick(item) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.name.of(locale), style = MaterialTheme.typography.bodyMedium)
                            Text(formatEur(item.priceEur), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
