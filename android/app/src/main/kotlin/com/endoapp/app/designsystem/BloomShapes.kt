package com.endoapp.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Bloom corner radii — a port of the iOS Bloom shape scale per `19-UI-SPEC.md` §Spacing, radius.
 *
 * M3 `Shapes`: `small`=8dp, `medium`=16dp, `large`=22dp (the Bloom card radius, iOS 22 `.continuous`).
 * Named [BloomPillShape] (fully rounded — chips, primary button, active tab pill) and
 * [BloomBannerShape] (16dp — disclaimer banner) cover the shapes without a clean M3 slot.
 */
val BloomShapes: Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** `radius.pill` — a fully-rounded capsule (chips, primary button, active tab pill). */
val BloomPillShape: RoundedCornerShape = RoundedCornerShape(50)

/** `radius.banner` — the disclaimer banner corner (16dp). */
val BloomBannerShape: RoundedCornerShape = RoundedCornerShape(16.dp)

/** `radius.card` — the Bloom card corner (22dp). */
val BloomCardShape: RoundedCornerShape = RoundedCornerShape(22.dp)
