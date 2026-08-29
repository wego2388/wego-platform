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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.content.siteInquiryUrl
import com.wego.mobile.customer.design.SdcSpace
import com.wego.mobile.shared.catalog.DiveSites
import com.wego.mobile.shared.catalog.Offering
import com.wego.mobile.shared.catalog.offerings
import com.wego.mobile.shared.locale.AppLocale

/** Mirrors `/dive-sites/[slug]` on the website: real site + the real, approved trips that visit it. */
@Composable
@Suppress("FunctionName")
fun DiveSiteDetailScreen(
    slug: String,
    locale: AppLocale,
    onBack: () -> Unit,
    onOfferingClick: (Offering) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBuildPackage: () -> Unit,
) {
    val site = DiveSites.bySlug(slug)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SdcSpace.xxl)) {
        TextButton(onClick = onBack) { Text(SiteCopy.DiveSites.back.of(locale)) }

        if (site == null) {
            Text(SiteCopy.Discover.notFound.of(locale), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm), modifier = Modifier.padding(top = SdcSpace.lg)) {
            Text(site.name.of(locale), style = MaterialTheme.typography.headlineSmall)
            Text(site.blurb.of(locale), style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = SdcSpace.lg))

        Text(SiteCopy.DiveSites.offeringsHeading.of(locale), style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(SdcSpace.sm), modifier = Modifier.padding(top = SdcSpace.sm)) {
            for (offering in site.offerings()) {
                OfferingRow(offering = offering, locale = locale, onClick = { onOfferingClick(offering) })
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(SdcSpace.md),
            modifier = Modifier.fillMaxWidth().padding(top = SdcSpace.xl),
        ) {
            Button(onClick = { onOpenUrl(siteInquiryUrl(site, locale)) }, modifier = Modifier.weight(1f)) {
                Text(SiteCopy.DiveSites.whatsappCta.of(locale))
            }
            OutlinedButton(onClick = onBuildPackage, modifier = Modifier.weight(1f)) {
                Text(SiteCopy.DiveSites.buildPackageCta.of(locale))
            }
        }
    }
}
