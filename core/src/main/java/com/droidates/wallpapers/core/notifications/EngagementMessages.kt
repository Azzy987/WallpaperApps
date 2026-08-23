package com.droidates.wallpapers.core.notifications

/**
 * Re-engagement notification copy.
 *
 * [categoryName] MUST match a main category `name` in the `Categories` collection
 * exactly — these are taken from `mainCategories` in the admin panel
 * (`wallpaper-dashboard-zenith/src/lib/firebase.ts`):
 *
 *  - "AMOLED & Dark"          (no subcategories)
 *  - "4K & Ultra HD"          (no subcategories)
 *  - "Minimal & Aesthetic"    → Abstract, Gradient, Typography
 *  - "Nature & Landscapes"    → Mountains, Beaches, Forests, Sky & Clouds
 *  - "Anime & Gaming"         → Anime Characters, Gaming Characters, Fantasy Worlds
 *
 * Subcategories are tabs *inside* a category screen, not separate destinations, so
 * every notification deep-links to the parent category. Where a message is written
 * about a subcategory, [subcategoryHint] names the tab the user should pick — it is
 * appended to the body so the copy stays honest about where the tap lands.
 */
data class EngagementMessage(
    val id: String,
    val title: String,
    val body: String,
    val categoryName: String,
    val subcategoryHint: String? = null
) {
    /** Body text as shown in the notification. */
    val displayBody: String
        get() = if (subcategoryHint != null) "$body · Open the $subcategoryHint tab" else body
}

object EngagementMessages {

    /**
     * Rotated in order so a user never sees the same line twice in a row.
     * Edit freely — only [EngagementMessage.categoryName] is load-bearing.
     */
    val all: List<EngagementMessage> = listOf(

        // ── AMOLED & Dark ────────────────────────────────────────────────────
        EngagementMessage(
            id = "amoled_battery",
            title = "True black, longer battery",
            body = "AMOLED wallpapers that switch off pixels instead of draining them 🖤",
            categoryName = "AMOLED & Dark"
        ),
        EngagementMessage(
            id = "amoled_night",
            title = "Easy on tired eyes",
            body = "Deep dark wallpapers made for late-night scrolling 🌙",
            categoryName = "AMOLED & Dark"
        ),
        EngagementMessage(
            id = "amoled_stealth",
            title = "Go full stealth",
            body = "Pure black backgrounds that make your icons pop ⚫",
            categoryName = "AMOLED & Dark"
        ),
        EngagementMessage(
            id = "amoled_fresh",
            title = "Your screen looks tired",
            body = "Fresh AMOLED picks to sharpen up your home screen ✨",
            categoryName = "AMOLED & Dark"
        ),

        // ── 4K & Ultra HD ────────────────────────────────────────────────────
        EngagementMessage(
            id = "4k_sharp",
            title = "Pixel-perfect, literally",
            body = "4K wallpapers that finally do your display justice 📱",
            categoryName = "4K & Ultra HD"
        ),
        EngagementMessage(
            id = "4k_detail",
            title = "Zoom in. It holds up.",
            body = "Ultra HD shots with detail you can actually see 🔍",
            categoryName = "4K & Ultra HD"
        ),
        EngagementMessage(
            id = "4k_upgrade",
            title = "Still on a blurry wallpaper?",
            body = "Upgrade to Ultra HD in two taps 🚀",
            categoryName = "4K & Ultra HD"
        ),
        EngagementMessage(
            id = "4k_new",
            title = "New in Ultra HD",
            body = "Crisp additions worth a look tonight 🌟",
            categoryName = "4K & Ultra HD"
        ),

        // ── Minimal & Aesthetic ──────────────────────────────────────────────
        EngagementMessage(
            id = "minimal_calm",
            title = "Less noise, more calm",
            body = "Minimal wallpapers to quiet a cluttered screen ✨",
            categoryName = "Minimal & Aesthetic"
        ),
        EngagementMessage(
            id = "minimal_abstract",
            title = "Feeling bored?",
            body = "Bold abstract art to shake up your home screen 🎨",
            categoryName = "Minimal & Aesthetic",
            subcategoryHint = "Abstract"
        ),
        EngagementMessage(
            id = "minimal_gradient",
            title = "Colour, smoothly done",
            body = "Soft gradients that make everything look expensive 🌈",
            categoryName = "Minimal & Aesthetic",
            subcategoryHint = "Gradient"
        ),
        EngagementMessage(
            id = "minimal_typography",
            title = "Say something",
            body = "Typography wallpapers with a little attitude 🔤",
            categoryName = "Minimal & Aesthetic",
            subcategoryHint = "Typography"
        ),

        // ── Nature & Landscapes ──────────────────────────────────────────────
        EngagementMessage(
            id = "nature_breather",
            title = "Need a breather?",
            body = "Landscapes that make your phone feel less loud 🌿",
            categoryName = "Nature & Landscapes"
        ),
        EngagementMessage(
            id = "nature_mountains",
            title = "Go big",
            body = "Mountain views worth waking your screen for 🏔️",
            categoryName = "Nature & Landscapes",
            subcategoryHint = "Mountains"
        ),
        EngagementMessage(
            id = "nature_beaches",
            title = "Pocket-sized holiday",
            body = "Beach wallpapers for the days you can't get away 🏖️",
            categoryName = "Nature & Landscapes",
            subcategoryHint = "Beaches"
        ),
        EngagementMessage(
            id = "nature_forests",
            title = "Touch some grass",
            body = "Deep green forest shots, no hiking required 🌲",
            categoryName = "Nature & Landscapes",
            subcategoryHint = "Forests"
        ),
        EngagementMessage(
            id = "nature_sky",
            title = "Look up",
            body = "Skies and clouds that make a lock screen feel open ☁️",
            categoryName = "Nature & Landscapes",
            subcategoryHint = "Sky & Clouds"
        ),

        // ── Anime & Gaming ───────────────────────────────────────────────────
        EngagementMessage(
            id = "anime_characters",
            title = "Your main character era",
            body = "Anime wallpapers picked for your home screen ⚡",
            categoryName = "Anime & Gaming",
            subcategoryHint = "Anime Characters"
        ),
        EngagementMessage(
            id = "gaming_characters",
            title = "Press start",
            body = "Gaming legends, now on your lock screen 🎮",
            categoryName = "Anime & Gaming",
            subcategoryHint = "Gaming Characters"
        ),
        EngagementMessage(
            id = "fantasy_worlds",
            title = "Somewhere else, for a second",
            body = "Fantasy worlds that beat staring at a plain background 🗡️",
            categoryName = "Anime & Gaming",
            subcategoryHint = "Fantasy Worlds"
        ),
    )

    /** Next message in rotation, based on how many have already been shown. */
    fun next(shownCount: Int): EngagementMessage = all[shownCount % all.size]
}
