package com.wego.mobile.sharmtogo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.wego.mobile.shared.catalog.TravelCatalogSnapshot
import com.wego.mobile.shared.catalog.TravelPriceBasis
import com.wego.mobile.shared.catalog.TravelService
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.sharmtogo.content.SiteCopy
import com.wego.mobile.sharmtogo.design.StgCard
import com.wego.mobile.sharmtogo.design.StgSpace

private fun priceBasisLabel(
    basis: TravelPriceBasis,
    locale: AppLocale,
): String =
    when (basis) {
        TravelPriceBasis.PER_GROUP -> SiteCopy.Browse.perGroup.of(locale)
        TravelPriceBasis.PER_VEHICLE -> SiteCopy.Browse.perVehicle.of(locale)
        else -> SiteCopy.Browse.perPerson.of(locale)
    }

private fun startingPrice(service: TravelService) = service.options.minByOrNull { it.priceAmount.toDoubleOrNull() ?: Double.MAX_VALUE }

@Composable
@Suppress("FunctionName")
fun ExperiencesScreen(
    locale: AppLocale,
    initialCategoryId: String? = null,
    onServiceClick: (TravelService) -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    val services =
        if (selectedCategoryId == null) {
            TravelCatalogSnapshot.services
        } else {
            TravelCatalogSnapshot.servicesByCategory(selectedCategoryId!!)
        }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(StgSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(StgSpace.sm),
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    label = { Text(SiteCopy.Browse.allCategories.of(locale)) },
                )
            }
            items(TravelCatalogSnapshot.categories) { category ->
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = { selectedCategoryId = category.id },
                    label = { Text(category.name.of(locale)) },
                )
            }
        }

        if (services.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(StgSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(StgSpace.sm),
            ) {
                Text(SiteCopy.Browse.emptyHeading.of(locale), style = MaterialTheme.typography.titleLarge)
                Text(SiteCopy.Browse.emptyBody.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(StgSpace.lg),
                verticalArrangement = Arrangement.spacedBy(StgSpace.md),
            ) {
                items(services) { service ->
                    val category = TravelCatalogSnapshot.categoryById(service.categoryId)
                    val price = startingPrice(service)
                    StgCard(modifier = Modifier.fillMaxWidth(), onClick = { onServiceClick(service) }) {
                        Column(modifier = Modifier.padding(StgSpace.lg), verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                            if (category != null) {
                                Text(
                                    category.name.of(locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(service.name.of(locale), style = MaterialTheme.typography.titleMedium)
                            Text(service.description.of(locale), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            service.operatedBy?.let {
                                Text(
                                    "${SiteCopy.Browse.operatedBy.of(locale)}: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (service.media.isNotEmpty()) {
                                Text(
                                    SiteCopy.Browse.photoCount(service.media.size).of(locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (price != null) {
                                Row {
                                    Text(
                                        "${SiteCopy.Browse.fromPrice.of(locale)} ${price.priceCurrency} ${price.priceAmount}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        " ${priceBasisLabel(price.priceBasis, locale)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
