// The pure offered-series / suppression filter — the Android mirror of the iOS
// `ExploreSelection.offered` (Features/Explore). NO Compose, NO android./androidx.: a plain value mapper
// so the suppression contract (a suppressed user is NEVER offered a bleeding series) is provable in a
// unit test WITHOUT rendering.
//
// DEFENSE-IN-DEPTH (threat T-22-01h): the `:insights` accessors already gate every bleeding value/
// occurrence on `!snap.suppressed`, so a suppressed user's bleeding produces no data. This filter is the
// SECOND wall — it removes the bleeding CHIPS from the picker entirely, so a suppressed user is never
// even OFFERED the series. Both walls must hold; this one is the render-facing one.
package com.endoapp.app.ui.explore

import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ExploreSeriesCatalog

/** The pure catalog-filter for the Explore series picker. */
object ExploreSelection {

    /** The two bleeding-derived series ids — removed from the offered catalog under suppression. Bleeding
     *  appears as BOTH a quantitative ordinal (`bleeding.intensity`) AND a presence event
     *  (`bleeding.event`); both must vanish for a suppressed user. */
    private val BLEEDING_IDS = setOf("bleeding.intensity", "bleeding.event")

    /**
     * The series offered in the picker for a user. When [suppressed], BOTH bleeding series are removed
     * (defense-in-depth over the accessor gate); otherwise the whole [ExploreSeriesCatalog.all] is offered
     * in its stable order. Pure value-in / value-out.
     */
    fun offered(suppressed: Boolean): List<ExploreSeries> =
        ExploreSeriesCatalog.all.filterNot { suppressed && it.id in BLEEDING_IDS }
}
