package com.droidates.wallpapers.core.utils

import android.content.Context

/**
 * Resolves per-app resources that shared code cannot reference directly.
 *
 * A library module's `R` only sees its own resources, so `R.mipmap.ic_launcher`
 * here would point at :core, not the installed app. Looking the id up by name
 * against the app's own package gives each app its real launcher icon.
 */
object AppIcons {

    /** The installed app's launcher icon, for notifications. */
    fun launcher(context: Context): Int {
        val id = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        return if (id != 0) id else context.applicationInfo.icon
    }
}
