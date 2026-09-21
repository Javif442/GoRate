package com.gorate.app.presentation.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gorate.app.R
import com.gorate.app.data.local.TripHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onNoteClick: (TripHistoryEntity) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<TripHistoryEntity, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    val selectedIds = mutableSetOf<Long>()
    var isSelectionMode = false

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        isSelectionMode = selectedIds.isNotEmpty()
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun selectAll(allIds: List<Long>) {
        selectedIds.clear()
        selectedIds.addAll(allIds)
        isSelectionMode = true
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        onSelectionChanged(0)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view, onNoteClick, ::toggleSelection, { isSelectionMode }, { selectedIds })
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        itemView: View,
        private val onNoteClick: (TripHistoryEntity) -> Unit,
        private val onToggleSelection: (Long) -> Unit,
        private val isSelectionModeGetter: () -> Boolean,
        private val selectedIdsGetter: () -> Set<Long>
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvHistoryDate)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvHistoryPrice)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvHistoryDetails)
        private val tvPerKm: TextView = itemView.findViewById(R.id.tvHistoryPerKm)
        private val tvPerHour: TextView = itemView.findViewById(R.id.tvHistoryPerHour)
        private val tvNote: TextView = itemView.findViewById(R.id.tvHistoryNote)
        private val tvNet: TextView = itemView.findViewById(R.id.tvHistoryNet)
        private val tvLocations: TextView = itemView.findViewById(R.id.tvHistoryLocations)
        private val tvAppOrigin: TextView = itemView.findViewById(R.id.tvAppOrigin)
        private val cbSelected: CheckBox = itemView.findViewById(R.id.cbSelected)
        private val efficiencyIndicator: View = itemView.findViewById(R.id.viewEfficiencyIndicator)
        private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        fun bind(trip: TripHistoryEntity) {
            val prefs = (itemView.context.applicationContext as com.gorate.app.GoRateApplication).tripRepository.prefsRepository
            val isMiles = prefs.isMilesEnabled()

            val unitLabel = if (isMiles) "mi" else "km"
            val displayDistance = if (isMiles) trip.distanceKm / 1.60934 else trip.distanceKm
            val displayPerKm = if (isMiles) trip.perKm * 1.60934 else trip.perKm

            tvDate.text = dateFormat.format(Date(trip.timestamp))
            tvAppOrigin.text = if (trip.appOrigin.contains("Entrega", ignoreCase = true)) "📦 Entrega" else "🚗 Chofer"
            tvPrice.text = String.format(Locale.US, "$%.2f", trip.price)
            tvDetails.text = "${String.format(Locale.US, "%.2f", displayDistance)} $unitLabel • ${trip.timeMin.toInt()} min"
            
            val pickup = trip.pickupLocation ?: ""
            val dropoff = trip.dropoffLocation ?: ""
            if (pickup.isNotBlank() || dropoff.isNotBlank()) {
                tvLocations.visibility = View.VISIBLE
                val pickupStr = if (pickup.isNotBlank()) "📍 $pickup" else ""
                val dropoffStr = if (dropoff.isNotBlank()) "🏁 $dropoff" else ""
                tvLocations.text = listOfNotNull(pickupStr.ifBlank { null }, dropoffStr.ifBlank { null }).joinToString("\n")
            } else {
                tvLocations.visibility = View.GONE
            }

            tvPerKm.text = String.format(Locale.US, "%.2f $/$unitLabel", displayPerKm)
            tvPerHour.text = String.format(Locale.US, "%.2f $/h", trip.perHour)
            
            if (prefs.isNetEarningsEnabled()) {
                tvNet.visibility = View.VISIBLE
                tvNet.text = String.format(Locale.US, "Ganancia Limpia: $%.2f", trip.netEarnings)
            } else {
                tvNet.visibility = View.GONE
            }
            
            tvNote.text = trip.note ?: "Toca para añadir una nota..."
            tvNote.setOnClickListener { onNoteClick(trip) }

            val isSelMode = isSelectionModeGetter()
            val selIds = selectedIdsGetter()

            if (isSelMode) {
                cbSelected.visibility = View.VISIBLE
                cbSelected.isChecked = selIds.contains(trip.id)
            } else {
                cbSelected.visibility = View.GONE
                cbSelected.isChecked = false
            }

            itemView.setOnClickListener {
                if (isSelMode) {
                    onToggleSelection(trip.id)
                } else {
                    onNoteClick(trip)
                }
            }

            itemView.setOnLongClickListener {
                onToggleSelection(trip.id)
                true
            }

            val colorRes = when {
                trip.perKm >= 0.80 -> R.color.status_green
                trip.perKm >= 0.50 -> R.color.status_yellow
                else -> R.color.status_red
            }
            efficiencyIndicator.setBackgroundResource(colorRes)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TripHistoryEntity>() {
        override fun areItemsTheSame(oldItem: TripHistoryEntity, newItem: TripHistoryEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TripHistoryEntity, newItem: TripHistoryEntity) = oldItem == newItem
    }
}
