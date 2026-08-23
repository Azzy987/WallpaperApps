package com.droidates.wallpapers.model

import androidx.compose.runtime.Stable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

@Stable
data class Wallpaper(
    val id: String = "",
    val wallpaperName: String = "",
    val imageUrl: String = "",
    val thumbnail: String = "",
    val category: String = "",
    val dimensions: String = "",
    val size: String = "",
    val downloads: Int = 0,
    val views: Int = 0,
    val timestamp: Timestamp = Timestamp.now(),
    val source: String = "",
    val exclusive: Boolean = false,
    val depthEffect: Boolean? = null,
    val series: String = "",
    val subCategory: String? = null,
    val launchYear: Int? = null,
    var isFavorite: Boolean = false
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): Wallpaper {
            return Wallpaper(
                id = doc.id,
                wallpaperName = doc.getString("wallpaperName") ?: "",
                imageUrl = doc.getString("imageUrl") ?: "",
                thumbnail = doc.getString("thumbnail") ?: "",
                category = doc.getString("category") ?: "",
                dimensions = doc.getString("dimensions") ?: "",
                size = doc.getString("size") ?: "",
                downloads = doc.getLong("downloads")?.toInt() ?: 0,
                views = doc.getLong("views")?.toInt() ?: 0,
                timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                source = doc.getString("source") ?: "",
                exclusive = doc.getBoolean("exclusive") ?: false,
                depthEffect = doc.getBoolean("depthEffect"),
                series = doc.getString("series") ?: "",
                subCategory = doc.getString("subCategory"),
                launchYear = doc.getLong("launchYear")?.toInt(),
            )
        }
    }
}