// file: com/example/arpackagevalidator/util/DialogHelper.kt
package com.example.arpackagevalidator.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.data.MeasurementData
import com.example.arpackagevalidator.ui.adapter.MeasurementAdapter
import java.text.SimpleDateFormat
import java.util.*

class DialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {

    fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showCalibrationDialog(onConfirm: (Float) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_calibration, null)
        val etKnownLength = dialogView.findViewById<EditText>(R.id.et_known_length)
        AlertDialog.Builder(context)
            .setTitle("Kalibrasi Pengukuran")
            .setMessage("Ukur objek dengan panjang yang sudah diketahui (misal: penggaris 30cm)")
            .setView(dialogView)
            .setPositiveButton("Mulai") { _, _ ->
                val knownLength = etKnownLength.text.toString().toFloatOrNull()
                if (knownLength != null && knownLength > 0) {
                    onConfirm(knownLength)
                } else {
                    showMessage("Masukkan panjang yang valid")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun showExportDialog(onCsvSelected: () -> Unit, onJsonSelected: () -> Unit) {
        val options = arrayOf("Export ke CSV", "Export ke JSON", "Simpan saja")
        AlertDialog.Builder(context)
            .setTitle("Pilih format export")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onCsvSelected()
                    1 -> onJsonSelected()
                    2 -> showMessage("Pengukuran berhasil disimpan")
                }
            }
            .show()
    }

    fun showHistoryDialog(
        history: List<MeasurementData>,
        onExport: () -> Unit,
        onDetail: (MeasurementData) -> Unit
    ) {
        if (history.isNullOrEmpty()) {
            showMessage("Belum ada riwayat pengukuran")
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_history, null)
        val rvMeasurements = dialogView.findViewById<RecyclerView>(R.id.rv_measurements)
        val adapter = MeasurementAdapter(history, onDetail)
        rvMeasurements.adapter = adapter
        rvMeasurements.layoutManager = LinearLayoutManager(context)

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Export Semua") { _, _ -> onExport() }
            .show()
    }

    fun showMeasurementDetailDialog(measurement: MeasurementData, onDelete: (MeasurementData) -> Unit) {
        val detailText = """
            Tanggal: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(measurement.timestamp))}
            Panjang: ${String.format("%.2f", measurement.length)} cm
            Lebar: ${String.format("%.2f", measurement.width)} cm
            Tinggi: ${String.format("%.2f", measurement.height)} cm
            Volume: ${String.format("%.4f", measurement.volume)} m³
            Klasifikasi: ${measurement.classification}
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("Detail Pengukuran")
            .setMessage(detailText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Hapus") { _, _ -> onDelete(measurement) }
            .show()
    }
}