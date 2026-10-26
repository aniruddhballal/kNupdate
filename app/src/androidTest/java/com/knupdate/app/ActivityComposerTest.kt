package com.knupdate.app

import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Edits drafts only: never sends an activity or changes existing history. */
class ActivityComposerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: SharedPreferences
    private var temporaryName = false
    private var previousName: String? = null

    @Before fun openComposer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("knupdate_local", 0)
        previousName = preferences.getString("display_name", null)
        temporaryName = previousName.isNullOrBlank()
        if (temporaryName) preferences.edit().putString("display_name", "UI test").commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("New activity").performClick()
    }

    @After fun closeComposer() {
        if (::scenario.isInitialized) scenario.close()
        if (temporaryName) preferences.edit().apply {
            if (previousName == null) remove("display_name") else putString("display_name", previousName)
        }.commit()
    }

    private fun text(description: String): String = compose.onNodeWithContentDescription(description)
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test fun nowAndManualTimesRetainTheirPrecisionAcrossRecreation() {
        compose.onNodeWithText("Eat").performScrollTo().performClick()
        compose.onNodeWithText("From–to").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("From current time").performScrollTo().performClick()
        val exact = text("From selected time")
        assertTrue(exact.matches(Regex("From  \\d{1,2}:\\d{2}:\\d{2} [AP]M")))
        scenario.recreate()
        compose.waitForIdle()
        assertEquals(exact, text("From selected time"))
        compose.onNodeWithContentDescription("From selected time").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        assertTrue(text("From selected time").matches(Regex("From  \\d{1,2}:\\d{2} [AP]M")))
        compose.onNodeWithContentDescription("From current time").performScrollTo().performClick()
        assertTrue(text("From selected time").matches(Regex("From  \\d{1,2}:\\d{2}:\\d{2} [AP]M")))
    }

    @Test fun gridModesAndBothNowLabelsFitWithoutCustomEntry() {
        compose.onNodeWithText("ACTIVITY").performScrollTo()
        compose.onNodeWithContentDescription("Movement activities").performScrollTo().performClick()
        compose.onNode(hasText("Run") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("From–to").performScrollTo().assertIsSelected()
        compose.onAllNodesWithText("Now", useUnmergedTree = true).assertCountEquals(2)
        repeat(2) { index ->
            val node = compose.onAllNodesWithText("Now", useUnmergedTree = true)[index]
            node.performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            val result = layouts.single()
            // Compare glyph bounds: a no-wrap paragraph may be wider than its visible text.
            assertTrue(result.getBoundingBox(0).left >= 0f)
            assertTrue(result.getBoundingBox(2).right <= result.size.width + 1f)
            assertFalse(result.didOverflowHeight)
        }
        compose.onNodeWithText("At", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("At current time").assertExists()
        compose.onNodeWithContentDescription("To current time").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("ACTIVITY").performScrollTo()
        compose.onNodeWithContentDescription("Travel activities").performScrollTo().performClick()
        compose.onNode(hasText("Arrived") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("At").performScrollTo().assertIsSelected()
    }
}
