package io.github.tomervancohen.rambleflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class UiTest {
    @get:Rule(order = 0)
    val reset =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                val app =
                    androidx.test.core.app.ApplicationProvider.getApplicationContext<RambleApp>()
                app.settings.step = 0
            }
        }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun standaloneSetupDoesNotEnableHostedProvider() {
        rule.onNode(hasText("Get started") or hasText("בואו נתחיל")).performClick()
        rule.onNodeWithText("Groq", substring = true).assertExists()
        rule.onNodeWithText("OpenAI", substring = true).assertExists()
        rule
            .onNode(hasText("Coming soon", substring = true) or hasText("בקרוב", substring = true))
            .assertExists()
        rule.onNode(hasText("Continue") or hasText("המשך")).assertIsDisplayed().performClick()
        rule.onNodeWithText("API key").assertExists()
    }
}
