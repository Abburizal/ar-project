package com.example.arpackagevalidator.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.example.arpackagevalidator.databinding.DialogInputBinding // <-- PERHATIKAN: Import ViewBinding

class DialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {

    fun showMessage(message: String) {
        // Menggunakan Toast untuk pesan singkat adalah pilihan yang baik.
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * PERBAIKAN: Fungsi ini sekarang menggunakan ViewBinding dan mencegah dialog
     * tertutup jika input tidak valid, memberikan pengalaman pengguna yang lebih baik.
     * Nama fungsi juga disesuaikan menjadi 'showCalibrationInputDialog'.
     */
    fun showCalibrationInputDialog(onConfirm: (Float) -> Unit) {
        // Menggunakan ViewBinding untuk mengakses layout dengan aman
        val binding = DialogInputBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Kalibrasi Jarak")
            .setView(binding.root)
            // Set listener ke null agar kita bisa mengontrolnya secara manual
            .setPositiveButton("OK", null)
            .setNegativeButton("Batal", null)
            .create()

        // Logika ini akan berjalan setelah dialog ditampilkan
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val distanceText = binding.inputEditText.text.toString()
                val distance = distanceText.toFloatOrNull()

                if (distance != null && distance > 0) {
                    // Jika input valid, panggil callback dan tutup dialog
                    onConfirm(distance)
                    dialog.dismiss()
                } else {
                    // Jika input tidak valid, tampilkan error dan JANGAN tutup dialog
                    binding.inputEditText.error = "Masukkan angka yang valid"
                }
            }
        }
        dialog.show()
    }

    fun showExportDialog(
        onCsvSelected: () -> Unit,
        onJsonSelected: () -> Unit
    ) {
        val options = arrayOf("CSV", "JSON")

        AlertDialog.Builder(context)
            .setTitle("Pilih Format Export")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onCsvSelected()
                    1 -> onJsonSelected()
                }
            }
            .show()
    }

    fun showHistoryDialog() {
        AlertDialog.Builder(context)
            .setTitle("Riwayat Pengukuran")
            .setMessage("Fitur riwayat belum tersedia")
            .setPositiveButton("OK", null)
            .show()
    }
}
