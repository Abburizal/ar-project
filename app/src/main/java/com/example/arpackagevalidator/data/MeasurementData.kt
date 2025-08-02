package com.example.arpackagevalidator.data

import java.util.Date

/**
 * Data class ini merepresentasikan satu entitas pengukuran yang sudah selesai.
 *
 * PERBAIKAN:
 * - Dibuat menjadi Plain Old Kotlin Object (POKO) murni, hanya berisi data.
 * - Logika kalkulasi dan helper method telah dipindahkan ke ViewModel atau lapisan yang sesuai.
 * - Tipe data 'points' diubah menjadi List<List<Float>> agar lebih fleksibel untuk
 * disimpan ke database atau diekspor ke JSON/CSV, karena Vector3 tidak bisa
 * langsung diserialisasi.
 */
data class MeasurementData(
    val id: Long,
    val timestamp: Date,
    val mode: String,
    val length: Float,
    val width: Float,
    val height: Float,
    val volume: Float, // Volume dalam cm³
    val classification: String,
    val points: List<List<Float>> // Disimpan sebagai List<List<Float>> untuk serialisasi
)
