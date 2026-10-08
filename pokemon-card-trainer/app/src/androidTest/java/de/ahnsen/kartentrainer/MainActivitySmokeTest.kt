package de.ahnsen.kartentrainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsCoreNavigation() {
        composeRule.onNodeWithText("Dein Karten-Abenteuer").assertIsDisplayed()
        composeRule.onNodeWithText("Karten scannen").assertIsDisplayed()
        composeRule.onNodeWithText("Meine Karten").assertIsDisplayed()
    }

    @Test
    fun scannerNavigationOpensLiveScannerArea() {
        composeRule.onNodeWithText("Karten scannen").performClick()
        composeRule.onNodeWithText("📸 Zeig mir deine Karte!").assertIsDisplayed()
        composeRule.onNodeWithText("Einzelscan").assertIsDisplayed()
    }
}
