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
        composeRule.onNodeWithText("📸 Karten entdecken!").assertIsDisplayed()
        composeRule.onNodeWithText("Wie möchtest du scannen?").assertExists()
        composeRule.onNodeWithText("Eine Karte scannen").assertExists()
        composeRule.onNodeWithText("Eine Karte genau scannen").assertExists()
        composeRule.onNodeWithText("Viele Karten nacheinander").assertExists()
        composeRule.onNodeWithText("Viele Karten auf einem Foto").assertExists()
        composeRule.onNodeWithText("📷 Karte scannen").assertExists()
    }
}
