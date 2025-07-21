package com.example.arpackagevalidator.data

data class MeasurementData(
    val timestamp: Long,
    val length: Float,
    val width: Float,
    val height: Float,
    val classification: String,
    val volume: Float
)