package com.droidates.wallpapers.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return value.split(",").filter { it.isNotBlank() }
    }

    @TypeConverter
    fun toString(list: List<String>): String {
        return list.joinToString(",")
    }
} 