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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wego.mobile.customer.content.SiteCopy
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
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBrowseAll) { Text(SiteCopy.Hero.browseCta.of(locale)) }
                OutlinedButton(onClick = onOpenWhatsApp) { Text(SiteCopy.Hero.whatsappCta.of(locale)) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (guarantee in SiteCopy.guarantees) {
                    Text("· ${guarantee.of(locale)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Text(
                SiteCopy.Discover.heading.of(locale),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(Categories.all) { category ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { onCategoryClick(category.id) },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        category.eyebrow.of(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(category.title.of(locale), style = MaterialTheme.typography.titleMedium)
                    Text(category.description.of(locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Text(
                SiteCopy.Discover.pricingNotice.of(locale),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }
}
