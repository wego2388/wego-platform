package com.wego.mobile.sharmtogo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.sharmtogo.content.SiteCopy
import com.wego.mobile.sharmtogo.design.StgBadge
import com.wego.mobile.sharmtogo.design.StgCard
import com.wego.mobile.sharmtogo.design.StgSpace

@Composable
@Suppress("FunctionName")
fun HomeScreen(
    locale: AppLocale,
    onBrowseAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(StgSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(StgSpace.xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(StgSpace.md)) {
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
            Button(onClick = onBrowseAll) { Text(SiteCopy.Hero.browseCta.of(locale)) }
        }

        item {
            StgCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    SiteCopy.marketplaceNotice.of(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(StgSpace.lg),
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(StgSpace.sm)) {
                Text(SiteCopy.How.heading.of(locale), style = MaterialTheme.typography.titleLarge)
            }
        }

        items(SiteCopy.How.steps) { (title, body) ->
            StgCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(StgSpace.lg),
                    verticalArrangement = Arrangement.spacedBy(StgSpace.xs),
                ) {
                    Text(title.of(locale), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(body.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(StgSpace.sm)) {
                Text(SiteCopy.Trust.heading.of(locale), style = MaterialTheme.typography.titleLarge)
                Text(SiteCopy.Trust.body.of(locale), style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StgSpace.sm), verticalArrangement = Arrangement.spacedBy(StgSpace.sm)) {
                for (point in SiteCopy.Trust.points) {
                    StgBadge(point.of(locale))
                }
            }
        }
    }
}
