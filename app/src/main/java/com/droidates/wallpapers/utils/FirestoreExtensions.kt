package com.droidates.wallpapers.utils

import com.droidates.wallpapers.config.AppConfig
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Returns the DocumentReference for a user in the nested collection structure:
 *   Users / iPhoneWallpapers / OnePlus7AndroidUsers / {userId}
 *
 * Used everywhere a user document needs to be read, created, or updated.
 */
fun FirebaseFirestore.userDocRef(userId: String): DocumentReference =
    collection(AppConfig.COLLECTION_USERS)
        .document(AppConfig.DOCUMENT_USERS_APP)
        .collection(AppConfig.COLLECTION_USERS_SUB)
        .document(userId)
