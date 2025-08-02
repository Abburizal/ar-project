package com.example.arpackagevalidator.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.arpackagevalidator.data.MeasurementData
import com.example.arpackagevalidator.utils.FileExporter
import kotlinx.coroutines.*

class MeasurementExportService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fileExporter: FileExporter? = null
    private lateinit var notificationManager: NotificationManager
    private val channelId = "export_channel"

    companion object {
        private const val TAG = "MeasurementExportService"
        const val EXPORT_FORMAT_CSV = "csv"
        const val EXPORT_FORMAT_JSON = "json"
        const val DATA_KEY = "DATA_TO_EXPORT"
        const val FORMAT_KEY = "EXPORT_FORMAT"
    }

    override fun onCreate() {
        super.onCreate()
        fileExporter = FileExporter(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dataToExport: List<MeasurementData>? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableArrayListExtra(DATA_KEY, MeasurementData::class.java)
            } else {
                // PERBAIKAN: Cara yang paling aman untuk versi Android lama
                // untuk mengatasi error "Not enough information to infer type variable T".
                @Suppress("DEPRECATION")
                val parcelables = intent?.getParcelableArrayListExtra<Parcelable>(DATA_KEY)
                // Kita filter list parcelable untuk memastikan hanya objek MeasurementData yang diambil.
                parcelables?.filterIsInstance<MeasurementData>()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving data from intent", e)
            sendNotification("Export Gagal", "Gagal mengambil data untuk diekspor.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (dataToExport.isNullOrEmpty()) {
            Log.e(TAG, "Tidak ada data untuk diekspor.")
            sendNotification("Export Gagal", "Tidak ada data untuk diekspor.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val exportFormat = intent?.getStringExtra(FORMAT_KEY) ?: EXPORT_FORMAT_CSV

        serviceScope.launch {
            try {
                val success = when (exportFormat) {
                    EXPORT_FORMAT_CSV -> fileExporter?.exportToCSV(dataToExport) != null
                    EXPORT_FORMAT_JSON -> fileExporter?.exportToJSON(dataToExport) != null
                    else -> false
                }

                if (success) {
                    val message = "Data Anda telah berhasil diekspor ke format ${exportFormat.uppercase()}."
                    sendNotification("Export Berhasil", message)
                } else {
                    throw IllegalStateException("Operasi ekspor gagal atau format tidak dikenal.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Terjadi error saat proses ekspor", e)
                sendNotification("Export Gagal", "Terjadi error: ${e.message}")
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notifikasi Export",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi untuk status proses ekspor"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Izin POST_NOTIFICATIONS tidak diberikan, notifikasi tidak dapat ditampilkan.")
            return
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
