package com.endoapp.app

import android.app.Application

/**
 * The `:app` [Application] — the composition-root lifetime holder (mirrors the iOS app object). It
 * builds ONE [AppDependencies] in [onCreate] and exposes it to [MainActivity]. No work beyond wiring
 * the dependency graph; the consent DataStore + StateFlow it constructs live for the whole process.
 */
class App : Application() {

    /** The single composition root, built in [onCreate]. */
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
    }
}
