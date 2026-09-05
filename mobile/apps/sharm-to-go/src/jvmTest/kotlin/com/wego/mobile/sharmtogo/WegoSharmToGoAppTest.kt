package com.wego.mobile.sharmtogo

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.sharmtogo.theme.StgTheme
import com.wego.mobile.sharmtogo.ui.screens.ExperienceDetailScreen
import kotlin.test.Test

/**
 * Real navigation/state coverage for the app shell, same JVM-target Compose
 * UI test approach [WegoCustomerAppTest] established. The real catalog is
 * empty as of this packet (Packet 1C's live verification), so these tests
 * cover the honest-empty-state and not-found paths — the ones a fabricated
 * fixture couldn't prove — rather than a populated catalog flow.
 */
@OptIn(ExperimentalTestApi::class)
class WegoSharmToGoAppTest {
    @Test
    fun `locale toggle flips visible text to Arabic`() =
        runComposeUiTest {
            setContent { WegoSharmToGoRoot() }
            onNodeWithText("Find the right Sharm experience, with local coordination you can understand.").assertExists()
            onNodeWithText("AR").performClick()
            onNodeWithText("اختار تجربة شرم المناسبة مع تنسيق محلي مفهوم وواضح.").assertExists()
        }

    @Test
    fun `browsing from Home shows the real, honest empty catalog state`() =
        runComposeUiTest {
            setContent { WegoSharmToGoRoot() }
            onNodeWithText("Explore categories").performClick()
            onNodeWithText("No live experiences yet").assertExists()
        }

    @Test
    fun `bottom navigation switches between Home and Experiences`() =
        runComposeUiTest {
            setContent { WegoSharmToGoRoot() }
            onNodeWithText("Experiences").performClick()
            onNodeWithText("No live experiences yet").assertExists()
            onNodeWithText("Sharm To Go").performClick()
            onNodeWithText("Find the right Sharm experience, with local coordination you can understand.").assertExists()
        }

    @Test
    fun `an unknown or unpublished service id shows an honest not-found state, not a crash`() =
        runComposeUiTest {
            setContent {
                StgTheme(locale = AppLocale.EN) {
                    ExperienceDetailScreen(id = "does-not-exist", locale = AppLocale.EN, onBack = {})
                }
            }
            onNodeWithText("This experience isn't available").assertExists()
            onNodeWithText("Back to experiences").assertExists()
        }
}
