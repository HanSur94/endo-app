package com.endoapp.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The bundled tab glyphs for the consented [ConsentedShell] nav bar, built in code as
 * [ImageVector]s per `19-UI-SPEC.md` §Iconography — the asset-light, first-party path that mirrors
 * the iOS SF-Symbol choices WITHOUT pulling `material-icons-extended` (heavy; against the
 * catalog-minimalism posture — threat T-19-SC "no new installs").
 *
 * Three distinct 24dp outline glyphs:
 *  - [logGlyph]      — an edit/pencil (iOS `pencil` / `Icons.Outlined.Edit` shape).
 *  - [insightsGlyph] — a rising line chart (iOS `chart.line.uptrend.xyaxis`).
 *  - [exploreGlyph]  — layered squares (iOS `square.stack.3d.up`), VISUALLY DISTINCT from the
 *    line-chart Insights glyph (a stacked-planes silhouette vs. a zig-zag line).
 *
 * All three are single-fill `SolidColor(Color.Black)` paths; the actual tint (plum selected /
 * inkMuted unselected) is applied by the `Icon` composable at the call site. Built lazily so the
 * `ImageVector.Builder` cost is paid once.
 */
object BloomTabIcons {

    /** Log tab — an edit/pencil glyph (create/log intent). */
    val logGlyph: ImageVector by lazy {
        ImageVector.Builder(
            name = "BloomLog",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Pencil body.
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3f, 17.25f)
                close()
                // Pencil tip / eraser cap.
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }.build()
    }

    /** Insights tab — a rising line chart (zig-zag polyline). */
    val insightsGlyph: ImageVector by lazy {
        ImageVector.Builder(
            name = "BloomInsights",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.5f, 18.49f)
                lineTo(9.5f, 12.48f)
                lineTo(13.5f, 16.48f)
                lineTo(22f, 6.92f)
                lineTo(20.59f, 5.51f)
                lineTo(13.5f, 13.48f)
                lineTo(9.5f, 9.48f)
                lineTo(2f, 16.99f)
                close()
            }
        }.build()
    }

    /** Explore tab — layered/stacked squares (distinct from the line-chart Insights glyph). */
    val exploreGlyph: ImageVector by lazy {
        ImageVector.Builder(
            name = "BloomExplore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Lower stacked plane.
                moveTo(11.99f, 18.54f)
                lineTo(4.62f, 12.81f)
                lineTo(3f, 14.07f)
                lineTo(12f, 21.07f)
                lineTo(21f, 14.07f)
                lineTo(19.37f, 12.8f)
                close()
                // Upper plane (the top layer of the stack).
                moveTo(12f, 16f)
                lineTo(19.36f, 10.27f)
                lineTo(21f, 9f)
                lineTo(12f, 2f)
                lineTo(3f, 9f)
                lineTo(4.63f, 10.27f)
                lineTo(12f, 16f)
                close()
            }
        }.build()
    }
}
