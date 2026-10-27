package com.knupdate.app

import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

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

    @Test fun liveClockTicksAndCustomTimeSurvivesRecreationUntilLiveIsSelected() {
        val live = text("Live current time")
        assertTrue(live.matches(Regex("Live  \\d{1,2}:\\d{2}:\\d{2} [AP]M")))
        compose.mainClock.autoAdvance = false
        try {
            Thread.sleep(1_200)
            compose.mainClock.advanceTimeBy(1_500)
            compose.waitForIdle()
            assertNotEquals(live, text("Live current time"))
        } finally { compose.mainClock.autoAdvance = true }

        scrollTo(hasText("Eat")).performClick()
        scrollTo(hasContentDescription("Set custom time")).performClick()
        compose.onNodeWithText("From–to").assertIsSelected()
        compose.onNodeWithText("At").performClick()
        compose.onNodeWithContentDescription("At selected time").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("USE TIME").performClick()
        val manual = text("At selected time")
        assertTrue(manual.matches(Regex("At  \\d{1,2}:\\d{2} [AP]M")))
        scenario.recreate()
        compose.waitForIdle()
        assertEquals(manual, text("At selected time"))
        scrollTo(hasContentDescription("Use live time")).performClick()
        scrollTo(hasContentDescription("Live current time")).assertExists()
        compose.onNodeWithContentDescription("At selected time").assertDoesNotExist()
    }

    @Test fun customRangeRequiresBothTimesAndCancelKeepsTheLiveDefault() {
        selectGroup("Movement")
        scrollTo(hasText("Run") and hasClickAction()).performClick()
        scrollTo(hasContentDescription("Set custom time")).performClick()
        compose.onNodeWithText("From–to").assertIsSelected()
        compose.onNodeWithText("USE TIME").assertIsNotEnabled()
        compose.onNodeWithContentDescription("From selected time").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("USE TIME").assertIsNotEnabled()
        compose.onNodeWithText("CANCEL").performClick()
        scrollTo(hasContentDescription("Live current time")).assertExists()
        compose.onNodeWithText("Now").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        selectGroup("Travel")
        scrollTo(hasText("Arrived") and hasClickAction()).performClick()
        scrollTo(hasContentDescription("Set custom time")).performClick()
        compose.onNodeWithText("At").assertIsSelected()
        compose.onNodeWithContentDescription("To selected time").assertDoesNotExist()
        compose.onNodeWithText("CANCEL").performClick()
    }

    private fun chooseTime(description: String, minutes: Int) {
        compose.onNodeWithContentDescription(description).performClick()
        listOf("hour" to minutes / 60, "minute" to minutes % 60).forEach { (field, value) ->
            val label = value.toString().padStart(2, '0')
            val tag = "time-value-$field"
            compose.onNodeWithTag(tag).performScrollToNode(hasText(label))
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag))).performClick()
        }
        compose.onNodeWithText("OK").performClick()
    }

    @Test fun customRangeSurvivesScrollingAndRecreationAndActivityChangesResetToLive() {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        org.junit.Assume.assumeTrue("Need two minutes of today's past for a range", now >= 2)
        selectGroup("Movement")
        scrollTo(hasText("Run") and hasClickAction()).performClick()
        scrollTo(hasContentDescription("Set custom time")).performClick()
        chooseTime("From selected time", now - 2)
        chooseTime("To selected time", now)
        compose.onNodeWithText("USE TIME").performClick()
        val start = text("From selected time")
        val end = text("To selected time")
        scenario.recreate()
        compose.waitForIdle()
        repeat(3) {
            compose.onNodeWithTag("activity-composer").performScrollToIndex(0)
            scrollTo(hasContentDescription("To selected time"))
        }
        assertEquals(start, text("From selected time"))
        assertEquals(end, text("To selected time"))
        scrollTo(hasText("Run") and hasClickAction()).assertIsSelected()
        selectGroup("Wellbeing")
        scrollTo(hasText("Medicine") and hasClickAction()).performClick()
        scrollTo(hasContentDescription("Live current time")).assertExists()
        scrollTo(hasContentDescription("Set custom time")).performClick()
        compose.onNodeWithText("At").assertIsSelected()
        compose.onNodeWithText("CANCEL").performClick()
    }
}
