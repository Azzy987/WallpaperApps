package com.droidates.wallpapers.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import androidx.compose.runtime.Immutable

@Immutable
data class User(
    @DocumentId
    val id: String = "",
    
    val email: String = "",
    
    val displayName: String = "",
    
    val photoUrl: String? = null,
    
    // Premium status fields
    @PropertyName("premium")
    val isPremium: Boolean = false,
    
    // Premium details
    @PropertyName("premiumType")
    val premiumType: String? = null,  // "monthly", "yearly", "lifetime"
    @PropertyName("premiumSince")
    val premiumSince: Date? = null,   // When premium subscription started
    @PropertyName("premiumExpiry")
    val premiumExpiry: Date? = null,  // When premium subscription expires (null for lifetime)
    
    @PropertyName("favorites")
    val favorites: List<String> = emptyList()
) 