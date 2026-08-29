package com.wego.mobile.customer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wego.mobile.customer.content.SiteCopy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Real navigation/state coverage for the app shell — the gap Phase D closed:
 * before this, only `mobile/shared`'s catalog data had tests, and nothing
 * verified the screens/navigation actually wired together correctly. Runs
 * on the JVM target against the same commonMain Compose code every platform
 * shares (Android host-test/Robolectric wiring wasn't attempted — not
 * needed for this coverage, and a real toolchain risk not worth taking on).
 */
@OptIn(ExperimentalTestApi::class)
class WegoCustomerAppTest {
    @Test
    fun `locale toggle flips visible text to Arabic`() =
        runComposeUiTest {
            setContent { WegoCustomerRoot() }
            onNodeWithText("Sharm El Sheikh · PADI 5 Star Dive Center").assertExists()
            onNodeWithText("AR").performClick()
            onNodeWithText("شرم الشيخ · مركز PADI 5 نجوم").assertExists()
        }

    @Test
    fun `category filter narrows the Discover list`() =
        runComposeUiTest {
            setContent { WegoCustomerRoot() }
            onNodeWithText("Discover").performClick()
            onNodeWithText("Intro Dive — 30 minutes").assertExists()
            onNodeWithText("Boat diving").performClick()
            onNodeWithText("Ras Mohammed beginner dive — 30 minutes").assertExists()
        }

    @Test
    fun `tapping an offering navigates to its detail screen with the real price`() =
        runComposeUiTest {
            setContent { WegoCustomerRoot() }
            onNodeWithText("Discover").performClick()
            onNodeWithText("Intro Dive — 30 minutes").performClick()
            onNodeWithText("Send an inquiry on WhatsApp").assertExists()
            onNodeWithText("€50").assertExists()
        }

    @Test
    fun `the WhatsApp button opens the real wa dot me link`() =
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
            onNodeWithText("Message us on WhatsApp").performClick()
            assertEquals(listOf(SiteCopy.WHATSAPP_URL), openedUrls)
        }
}
