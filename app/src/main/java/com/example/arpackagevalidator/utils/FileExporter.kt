// file: com/example/arpackagevalidator/util/FileExporter.kt
package com.example.arpackagevalidator.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.arpackagevalidator.data.MeasurementData

class FileExporter(private val context: Context) {

    private fun getExportFile(extension: String): File {
        val fileName = "measurements_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$extension"
        val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
        return File(directory, fileName)
    }

    fun exportToCSV(history: List<MeasurementData>): String {
        try {
            val file = getExportFile("csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Date,Length(cm),Width(cm),Height(cm),Volume(m³),Classification\n")
                history.forEach { data ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(data.timestamp))
                    writer.write("${data.timestamp},$date,${data.length},${data.width},${data.height},${String.format("%.4f", data.volume)},${data.classification}\n")
                }
            }
            return file.absolutePath
        } catch (e: Exception) {
            return "Error export CSV: ${e.message}"
        }
    }

    fun exportToJSON(history: List<MeasurementData>): String {
        try {
            val file = getExportFile("json")
            val jsonArray = JSONArray()
            history.forEach { data ->
                val jsonObject = JSONObject().apply {
                    put("timestamp", data.timestamp)
                    put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(data.timestamp)))
                    put("dimensions", JSONObject().apply {
                        put("length_cm", data.length)
                        put("width_cm", data.width)
                        put("height_cm", data.height)
                    })
                    put("volume_m3", String.format("%.4f", data.volume))
                    put("classification", data.classification)
                }
                jsonArray.put(jsonObject)
            }
            file.writeText(jsonArray.toString(2))
            return file.absolutePath
        } catch (e: Exception) {
            return "Error export JSON: ${e.message}"
        }
    }
}