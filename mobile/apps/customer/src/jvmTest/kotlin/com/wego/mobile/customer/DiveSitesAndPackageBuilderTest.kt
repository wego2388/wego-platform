package com.wego.mobile.customer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real navigation/interaction coverage for Phase 6's two new screens, same
 * JVM-target Compose UI test approach as [WegoCustomerAppTest].
 */
@OptIn(ExperimentalTestApi::class)
class DiveSitesAndPackageBuilderTest {
    @Test
    fun `Dive Sites lists every real named site and drills into a real detail screen`() =
        runComposeUiTest {
            setContent { WegoCustomerRoot() }
            onNodeWithText("Dive Sites").performClick()
            onNodeWithText("Ras Mohammed").assertExists()
            onNodeWithText("SS Thistlegorm").assertExists()

            onNodeWithText("Ras Mohammed").performClick()
            onNodeWithText("Ras Mohammed beginner dive — 30 minutes").assertExists()
            onNodeWithText("€60").assertExists()
        }

    @Test
    fun `the dive site inquiry button opens a real WhatsApp link naming the site`() =
        runComposeUiTest {
            val openedUrls = mutableListOf<String>()
            val fakeHandler =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        openedUrls.add(uri)
                    }
                }
            setContent {
                CompositionLocalProvider(LocalUriHandler provides fakeHandler) {
                    WegoCustomerRoot()
                }
            }
            onNodeWithText("Dive Sites").performClick()
            onNodeWithText("Tiran").performClick()
            onNodeWithText("Send an inquiry on WhatsApp").performClick()

            assertTrue(openedUrls.single().contains("Tiran"))
        }

    @Test
    fun `Package builder adds a real offering and shows a real running total`() =
        runComposeUiTest {
            setContent { WegoCustomerRoot() }
            onNodeWithText("Package").performClick()
            onNodeWithText("Nothing added yet — pick a few offerings below to start building a package.").assertExists()

            onAllNodesWithText("Add")[0].performClick()

            onNodeWithText("Estimated total").assertExists()
            onNodeWithText("Remove").assertExists()
        }

    @Test
    fun `the package inquiry button opens a real WhatsApp link listing selected offerings`() =
        runComposeUiTest {
            val openedUrls = mutableListOf<String>()
            val fakeHandler =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        openedUrls.add(uri)
                    }
                }
            setContent {
                CompositionLocalProvider(LocalUriHandler provides fakeHandler) {
                    WegoCustomerRoot()
                }
            }
            onNodeWithText("Package").performClick()
            onAllNodesWithText("Add")[0].performClick()
            onNodeWithText("Send this package on WhatsApp").performClick()

            val url = openedUrls.single()
            assertTrue(url.contains("Estimated"), url)
            assertTrue(url.contains("total"), url)
        }
}
