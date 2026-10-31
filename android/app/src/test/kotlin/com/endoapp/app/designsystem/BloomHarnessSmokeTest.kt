package com.endoapp.app.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The load-bearing prerequisite for every later Compose test in Phases 19-24: proves the
 * JVM Robolectric + Compose lane actually renders a composable and can query the semantics tree
 * WITHOUT an emulator. Plan 02's consent-by-construction gate test depends on this harness.
 *
 * `@Config(sdk = [34])` pins a Robolectric-stable Compose SDK for the JVM lane — the emulator
 * lane (`connectedDebugAndroidTest`) exercises the real targetSdk 36 behaviors. Robolectric 4.16.1
 * supports SDK 36, but 34 is the widely-proven Compose-under-Robolectric baseline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BloomHarnessSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composableRendersOnTheJvmLane() {
        composeRule.setContent {
            Text("harness", modifier = Modifier.testTag("harness.smoke"))
        }

        composeRule.onNodeWithTag("harness.smoke").assertExists()
    }
}
