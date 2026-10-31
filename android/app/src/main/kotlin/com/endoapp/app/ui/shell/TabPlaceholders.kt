package com.endoapp.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH3

/**
 * `TabPlaceholder` — the honest "arrives soon" body a consented-shell tab renders THIS phase: a
 * centered Bloom [bloomCard] carrying an [EyebrowLabel] + a `bloomH3` heading + a `bloomBody` line,
 * per `19-UI-SPEC.md` §Consented shell tabs. The real tab content is deferred to Phases 20/22; this
 * card names the tab and sets the "coming update" expectation without faking data.
 *
 * The card's descendants are merged into one semantics node ([semantics] `mergeDescendants`) so
 * TalkBack reads the eyebrow + heading + body as a single announcement rather than three fragments.
 * The [testTag] (e.g. `tab.log.body`) preserves the Plan-02 per-tab body contract used by the shell
 * test and the iOS XCUITest parity ids.
 */
@Composable
fun TabPlaceholder(
    eyebrow: String,
    heading: String,
    body: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .bloomCard()
                .testTag(testTag)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EyebrowLabel(eyebrow)
            Text(text = heading, style = bloomH3, color = colors.ink)
            Text(text = body, style = bloomBody, color = colors.inkSecondary)
        }
    }
}
