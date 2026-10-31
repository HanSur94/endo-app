// EndoApp (:app) — the BodyMap silhouette geometry (SYMP-02, Plan 20-03). A Compose [Path] human
// silhouette authored in a 200×440 view-box plus the normalized hotspot-center table (verbatim from
// the iOS `BodySilhouette.hotspotCenter`) and the front/back region sets.
//
// The GEOMETRY is NOT the load-bearing contract — the accessibility contract ([BodyMapView]'s per-region
// focusable ≥48dp semantics + the list fallback) is. The silhouette may iterate later without any
// contract change; only the hotspot centers + side sets are part of the parity surface.
package com.endoapp.app.ui.tracking

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import com.endoapp.insights.BodyRegion

/** Which silhouette side a hotspot belongs to. Front and back share one silhouette; only the set differs. */
enum class BodySide { FRONT, BACK }

/** A single hotspot: its [region], normalized center ([x], [y] in the 0..1 view-box), and its [side]. */
data class BodyHotspot(val region: BodyRegion, val x: Float, val y: Float, val side: BodySide)

/**
 * The silhouette geometry + the hotspot table. The [hotspots] centers are VERBATIM from the iOS
 * `hotspotCenter` table (UI-SPEC §2); [regionsFor] returns the current side's regions in
 * [BodyRegion.entries] order (the SYMP-02 focus order).
 */
object BodySilhouette {

    /** Author view-box width (normalized coords scale to the rendered size). */
    const val VIEW_W: Float = 200f

    /** Author view-box height. */
    const val VIEW_H: Float = 440f

    /** The aspect ratio the [BodyMapView] Canvas preserves. */
    const val ASPECT: Float = VIEW_W / VIEW_H

    /** The normalized hotspot centers (verbatim from iOS `hotspotCenter`, UI-SPEC §2 table). */
    val hotspots: List<BodyHotspot> = listOf(
        // Front
        BodyHotspot(BodyRegion.SHOULDER, 0.500f, 0.155f, BodySide.FRONT),
        BodyHotspot(BodyRegion.CHEST_DIAPHRAGM, 0.500f, 0.239f, BodySide.FRONT),
        BodyHotspot(BodyRegion.LOWER_ABDOMEN_PELVIS, 0.500f, 0.398f, BodySide.FRONT),
        BodyHotspot(BodyRegion.LOWER_QUADRANT_LEFT, 0.380f, 0.420f, BodySide.FRONT),
        BodyHotspot(BodyRegion.LOWER_QUADRANT_RIGHT, 0.620f, 0.420f, BodySide.FRONT),
        BodyHotspot(BodyRegion.BLADDER, 0.500f, 0.482f, BodySide.FRONT),
        BodyHotspot(BodyRegion.UPPER_THIGHS, 0.500f, 0.595f, BodySide.FRONT),
        // Back
        BodyHotspot(BodyRegion.LOWER_BACK, 0.500f, 0.364f, BodySide.BACK),
        BodyHotspot(BodyRegion.HIPS, 0.500f, 0.445f, BodySide.BACK),
        BodyHotspot(BodyRegion.RECTAL_BOWEL, 0.500f, 0.518f, BodySide.BACK),
    )

    /** The hotspots of [side], in [BodyRegion.entries] order (the SYMP-02 hotspot focus order). */
    fun hotspotsFor(side: BodySide): List<BodyHotspot> {
        val bySide = hotspots.filter { it.side == side }
        return BodyRegion.entries.mapNotNull { region -> bySide.firstOrNull { it.region == region } }
    }

    /**
     * Build the silhouette outline [Path] scaled to [size] (aspect preserved by the caller). A calm,
     * androgynous humanoid blob — head, torso, arms, legs — in normalized coords scaled to the render
     * box. Deliberately simple; geometry is decorative (the hotspots carry the meaning).
     */
    fun path(size: Size): Path {
        val w = size.width
        val h = size.height
        fun px(nx: Float) = nx * w
        fun py(ny: Float) = ny * h
        return Path().apply {
            // Head
            moveTo(px(0.50f), py(0.03f))
            cubicTo(px(0.61f), py(0.03f), px(0.61f), py(0.15f), px(0.50f), py(0.16f))
            cubicTo(px(0.39f), py(0.15f), px(0.39f), py(0.03f), px(0.50f), py(0.03f))
            close()
            // Body + limbs (single blob outline)
            moveTo(px(0.44f), py(0.17f))
            lineTo(px(0.56f), py(0.17f))                       // neck
            cubicTo(px(0.70f), py(0.19f), px(0.78f), py(0.23f), px(0.80f), py(0.30f)) // right shoulder
            lineTo(px(0.90f), py(0.55f))                       // right arm out
            lineTo(px(0.83f), py(0.57f))
            lineTo(px(0.72f), py(0.34f))                       // right arm in
            lineTo(px(0.70f), py(0.55f))                       // right waist
            lineTo(px(0.66f), py(0.98f))                       // right leg out
            lineTo(px(0.56f), py(0.98f))
            lineTo(px(0.50f), py(0.66f))                       // crotch
            lineTo(px(0.44f), py(0.98f))
            lineTo(px(0.34f), py(0.98f))                       // left leg
            lineTo(px(0.30f), py(0.55f))
            lineTo(px(0.28f), py(0.34f))                       // left waist
            lineTo(px(0.17f), py(0.57f))                       // left arm in
            lineTo(px(0.10f), py(0.55f))
            lineTo(px(0.20f), py(0.30f))                       // left arm out
            cubicTo(px(0.22f), py(0.23f), px(0.30f), py(0.19f), px(0.44f), py(0.17f)) // left shoulder
            close()
        }
    }
}
