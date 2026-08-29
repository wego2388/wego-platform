package com.wego.mobile.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.design.SdcCard
import com.wego.mobile.customer.design.SdcColor
import com.wego.mobile.customer.design.SdcMockPhoto
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.shared.catalog.Categories
import com.wego.mobile.shared.catalog.CategoryId
import com.wego.mobile.shared.catalog.DiveCatalog
import com.wego.mobile.shared.catalog.Offering
import com.wego.mobile.shared.catalog.diveCountLabel
import com.wego.mobile.shared.catalog.durationLabel
import com.wego.mobile.shared.catalog.formatEur
import com.wego.mobile.shared.catalog.label
import com.wego.mobile.shared.locale.AppLocale

@Composable
@Suppress("FunctionName")
fun DiscoverScreen(
    locale: AppLocale,
    initialCategory: CategoryId?,
    onOfferingClick: (Offering) -> Unit,
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    val offerings =
        if (selectedCategory == null) {
            DiveCatalog.offerings
        } else {
            DiveCatalog.byCategory(selectedCategory!!)
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(SiteCopy.Discover.heading.of(locale), style = MaterialTheme.typography.titleLarge)
            Text(SiteCopy.Discover.body.of(locale), style = MaterialTheme.typography.bodyMedium)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(SiteCopy.Discover.filterAll.of(locale)) },
                )
            }
            items(Categories.all) { category ->
                FilterChip(
                    selected = selectedCategory == category.id,
                    onClick = { selectedCategory = category.id },
                    label = { Text(category.title.of(locale)) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(offerings, key = { it.code }) { offering ->
                OfferingRow(offering = offering, locale = locale, onClick = { onOfferingClick(offering) })
            }
            item {
                Text(
                    SiteCopy.Discover.pricingNotice.of(locale),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
fun OfferingRow(
    offering: Offering,
    locale: AppLocale,
    onClick: () -> Unit,
) {
    SdcCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SdcSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(SdcSpace.lg),
        ) {
            SdcMockPhoto(modifier = Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SdcSpace.xs)) {
                Text(offering.name.of(locale), style = MaterialTheme.typography.titleMedium)
                Text(offering.audience.label(locale), style = MaterialTheme.typography.bodySmall, color = SdcColor.textSecondary)
                val meta =
                    listOfNotNull(
                        durationLabel(locale, offering.durationMinutes),
                        diveCountLabel(locale, offering.diveCount),
                    ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = SdcColor.deepBright)
                }
            }
            Text(
                formatEur(offering.priceEur),
                style = MaterialTheme.typography.titleMedium,
                color = SdcColor.deepBright,
            )
        }
    }
}
