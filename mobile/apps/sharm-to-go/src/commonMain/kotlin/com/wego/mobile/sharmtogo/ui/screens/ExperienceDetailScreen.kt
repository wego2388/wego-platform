package com.wego.mobile.sharmtogo.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Real service detail — bilingual name/description, real options/pricing,
 * cancellation policy, pickup/inclusions/exclusions when present, the
 * provider's name for a PARTNER service, and an honest, clearly
 * non-functional contact placeholder. Never a "Book now" action: no real
 * Sharm To Go contact channel exists yet (confirmed against this client's own
 * markdown docs and `client.manifest.json` — see the website's own Packet 1C
 * entry on the execution board for the same finding).
 */
@Composable
@Suppress("FunctionName")
fun ExperienceDetailScreen(
    id: String,
    locale: AppLocale,
    onBack: () -> Unit,
) {
    val service = TravelCatalogSnapshot.serviceById(id)

    if (service == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(StgSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(StgSpace.md),
        ) {
            Text(SiteCopy.Detail.notFoundHeading.of(locale), style = MaterialTheme.typography.headlineSmall)
            Text(SiteCopy.Detail.notFoundBody.of(locale), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBack) { Text(SiteCopy.Detail.back.of(locale)) }
        }
        return
    }

    ExperienceDetailContent(service, locale, onBack)
}

@Composable
@Suppress("FunctionName")
private fun ExperienceDetailContent(
    service: TravelService,
    locale: AppLocale,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(StgSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(StgSpace.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                Text(service.name.of(locale), style = MaterialTheme.typography.headlineSmall)
                service.operatedBy?.let {
                    Text(
                        "${SiteCopy.Browse.operatedBy.of(locale)}: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(service.description.of(locale), style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Text(SiteCopy.Detail.optionsHeading.of(locale), style = MaterialTheme.typography.titleMedium)
        }

        items(service.options) { option ->
            StgCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(StgSpace.lg).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(option.label.of(locale), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${option.priceCurrency} ${option.priceAmount} · ${priceBasisLabel(option.priceBasis, locale)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                Text(SiteCopy.Detail.cancellationHeading.of(locale), style = MaterialTheme.typography.titleMedium)
                Text(service.cancellationPolicy.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        }

        service.pickupInfo?.let { pickup ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                    Text(SiteCopy.Detail.pickupHeading.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(pickup.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        service.inclusions?.let { inclusions ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                    Text(SiteCopy.Detail.inclusionsHeading.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(inclusions.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        service.exclusions?.let { exclusions ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                    Text(SiteCopy.Detail.exclusionsHeading.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(exclusions.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (service.media.isNotEmpty()) {
            item {
                Text(
                    SiteCopy.Browse.photoCount(service.media.size).of(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            StgCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(StgSpace.lg), verticalArrangement = Arrangement.spacedBy(StgSpace.xs)) {
                    Text(SiteCopy.Detail.contactHeading.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(SiteCopy.Detail.contactBody.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Button(onClick = onBack) { Text(SiteCopy.Detail.back.of(locale)) }
        }
    }
}
