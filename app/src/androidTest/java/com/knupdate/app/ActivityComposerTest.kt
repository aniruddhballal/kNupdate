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

    private fun scrollTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("activity-composer").performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    private fun selectGroup(name: String) {
        compose.onNodeWithTag("activity-composer").performScrollToIndex(0)
        compose.onNodeWithTag("activity-groups").performScrollToNode(hasContentDescription("$name activities"))
        compose.onNodeWithContentDescription("$name activities").performClick()
    }

    private fun text(description: String): String = scrollTo(hasContentDescription(description))
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test fun nowAndManualTimesRetainTheirPrecisionAcrossRecreation() {
        scrollTo(hasText("Eat")).performClick()
        scrollTo(hasText("From–to")).assertIsSelected()
        scrollTo(hasContentDescription("From current time")).performClick()
        val exact = text("From selected time")
        assertTrue(exact.matches(Regex("From  \\d{1,2}:\\d{2}:\\d{2} [AP]M")))
        scenario.recreate()
        compose.waitForIdle()
        assertEquals(exact, text("From selected time"))
        scrollTo(hasContentDescription("From selected time")).performClick()
        compose.onNodeWithText("OK").performClick()
        assertTrue(text("From selected time").matches(Regex("From  \\d{1,2}:\\d{2} [AP]M")))
        scrollTo(hasContentDescription("From current time")).performClick()
        assertTrue(text("From selected time").matches(Regex("From  \\d{1,2}:\\d{2}:\\d{2} [AP]M")))
    }

    @Test fun gridModesAndBothNowLabelsFitWithoutCustomEntry() {
        selectGroup("Movement")
        scrollTo(hasText("Run") and hasClickAction()).performClick()
        scrollTo(hasText("From–to")).assertIsSelected()
        listOf("From", "To").forEach { label ->
            val description = "$label current time"
            scrollTo(hasContentDescription(description))
            val node = compose.onNode(hasText("Now") and hasAnyAncestor(hasContentDescription(description)), useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            val result = layouts.single()
            // Compare glyph bounds: a no-wrap paragraph may be wider than its visible text.
            assertTrue(result.getBoundingBox(0).left >= 0f)
            assertTrue(result.getBoundingBox(2).right <= result.size.width + 1f)
            assertFalse(result.didOverflowHeight)
        }
        scrollTo(hasText("At")).performClick()
        scrollTo(hasContentDescription("At current time")).assertExists()
        compose.onNodeWithContentDescription("To current time").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        selectGroup("Travel")
        scrollTo(hasText("Arrived") and hasClickAction()).performClick()
        scrollTo(hasText("At")).assertIsSelected()
    }

    @Test fun scrollingDoesNotChangeDraftSelectionOrTime() {
        selectGroup("Movement")
        scrollTo(hasText("Run") and hasClickAction()).performClick()
        scrollTo(hasContentDescription("From current time")).performClick()
        val start = text("From selected time")
        repeat(3) {
            compose.onNodeWithTag("activity-composer").performScrollToIndex(0)
            scrollTo(hasContentDescription("To current time"))
        }
        assertEquals(start, text("From selected time"))
        scrollTo(hasText("Run") and hasClickAction()).assertIsSelected()
        selectGroup("Wellbeing")
        scrollTo(hasText("Medicine") and hasClickAction()).performClick()
        scrollTo(hasText("At")).assertIsSelected()
    }
}
