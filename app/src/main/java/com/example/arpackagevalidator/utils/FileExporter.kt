package com.example.arpackagevalidator.utils

import android.content.Context
import android.os.Environment
import com.example.arpackagevalidator.data.MeasurementData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class FileExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    fun exportToCSV(measurements: List<MeasurementData>): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "measurements_$timestamp.csv"
            val file = File(getExportDirectory(), fileName)

            FileWriter(file).use { writer ->
                // CSV Header
                writer.append("Timestamp,Mode,Length(cm),Width(cm),Height(cm),Volume(m³),Classification\n")

                // CSV Data
                measurements.forEach { measurement ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    writer.append("${dateFormat.format(measurement.timestamp)},")
                    writer.append("${measurement.mode},")
                    writer.append("${String.format("%.2f", measurement.length)},")
                    writer.append("${String.format("%.2f", measurement.width)},")
                    writer.append("${String.format("%.2f", measurement.height)},")
                    writer.append("${String.format("%.6f", measurement.volume)},")
                    writer.append("${measurement.classification}\n")
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun exportToJSON(measurements: List<MeasurementData>): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "measurements_$timestamp.json"
            val file = File(getExportDirectory(), fileName)

            val jsonData = mapOf(
                "export_date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "total_measurements" to measurements.size,
                "measurements" to measurements
            )

            FileWriter(file).use { writer ->
                gson.toJson(jsonData, writer)
            }

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getExportDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ARPackageValidator")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
