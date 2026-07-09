package com.endoapp.app.ui.shell

import com.endoapp.app.content.ShellCopy

/**
 * The three consented-shell destinations (Log / Insights / Explore) — a typed enum, NEVER an `Int`
 * index. Each carries the stable [testTag] matching the iOS XCUITest ids and its [label] sourced
 * from the registered [ShellCopy]. `ConsentedTab` is referenced only inside [AppGate]'s `Consented`
 * arm (via `ConsentedShell`), so the tab surface is unconstructible pre-consent by construction.
 */
enum class ConsentedTab(val testTag: String, val label: String) {
    Log("tab.log", ShellCopy.TAB_LOG),
    Insights("tab.insights", ShellCopy.TAB_INSIGHTS),
    Explore("tab.explore", ShellCopy.TAB_EXPLORE),
}
