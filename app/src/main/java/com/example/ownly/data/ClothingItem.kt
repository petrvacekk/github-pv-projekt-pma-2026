package com.example.ownly.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val description: String,
    // Use the smallest currency unit (e.g. haléře) to store the price exactly.
    val purchasePriceInMinorUnits: Long
)
