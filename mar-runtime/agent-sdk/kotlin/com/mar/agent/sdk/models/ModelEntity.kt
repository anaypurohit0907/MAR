package com.mar.agent.sdk.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeMb: Long,
    val quantization: String,
    val family: String,
    val parameters: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val filePath: String = ""
)
