package com.example.arpackagevalidator.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.arpackagevalidator.data.MeasurementData
import com.example.arpackagevalidator.R
import java.text.SimpleDateFormat
import java.util.*

class MeasurementAdapter(
    private val measurements: List<MeasurementData>,
    private val onItemClick: (MeasurementData) -> Unit
) : RecyclerView.Adapter<MeasurementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvDimensions: TextView = view.findViewById(R.id.tv_dimensions)
        val tvClassification: TextView = view.findViewById(R.id.tv_classification)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val measurement = measurements[position]
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        holder.tvDate.text = dateFormat.format(Date(measurement.timestamp))
        holder.tvDimensions.text = "${measurement.length} × ${measurement.width} × ${measurement.height} cm"
        holder.tvClassification.text = measurement.classification

        // Set warna berdasarkan klasifikasi
        val color = when (measurement.classification) {
            "SMALL" -> android.graphics.Color.GREEN
            "MEDIUM" -> android.graphics.Color.BLUE
            "LARGE" -> android.graphics.Color.MAGENTA
            else -> android.graphics.Color.RED
        }
        holder.tvClassification.setTextColor(color)

        holder.itemView.setOnClickListener {
            onItemClick(measurement)
        }
    }

    override fun getItemCount() = measurements.size
}