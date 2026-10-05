package de.ahnsen.kartentrainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.onNodeWithText("Mit echten Karten lernen und spielen").assertIsDisplayed()
        composeRule.onNodeWithText("Karten scannen").assertIsDisplayed()
        composeRule.onNodeWithText("Meine Karten").assertIsDisplayed()
        composeRule.onNodeWithText("Deck-Werkstatt").assertIsDisplayed()
        composeRule.onNodeWithText("KI-Trainer").assertIsDisplayed()
        composeRule.onNodeWithText("2-Spieler-Tischhelfer").assertIsDisplayed()
        composeRule.onNodeWithText("Profile & lokales Backup").assertIsDisplayed()
    }

    @Test
    fun learningNavigationOpensGuidedArea() {
        composeRule.onNodeWithText("Spielen lernen").performClick()
        composeRule.onNodeWithText("Regelkurs").assertIsDisplayed()
        composeRule.onNodeWithText("Geführtes Erstspiel").assertIsDisplayed()
    }

    @Test
    fun deckNavigationOpensWorkshop() {
        composeRule.onNodeWithText("Deck-Werkstatt").performClick()
        composeRule.onNodeWithText("Deck-Werkstatt 2.0").assertIsDisplayed()
    }
}
