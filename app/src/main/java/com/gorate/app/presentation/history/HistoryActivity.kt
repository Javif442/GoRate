package com.gorate.app.presentation.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import com.gorate.app.data.local.TripHistoryEntity
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

import androidx.core.view.isVisible

class HistoryActivity : AppCompatActivity() {

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory((application as GoRateApplication).tripRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)
        val emptyState = findViewById<View>(R.id.emptyState)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFilters)
        val tvTotalEarnings = findViewById<TextView>(R.id.tvTotalEarnings)
        val tvTotalNet = findViewById<TextView>(R.id.tvTotalNet)
        val tvAvgEfficiency = findViewById<TextView>(R.id.tvAvgEfficiency)
        val btnClearHistory = findViewById<View>(R.id.btnClearHistory)
        val btnCalendar = findViewById<View>(R.id.btnCalendar)
        val btnSearch = findViewById<android.widget.ImageButton>(R.id.btnSearch)
        val btnExportCsv = findViewById<android.widget.ImageButton>(R.id.btnExportCsv)
        val layoutSearch = findViewById<View>(R.id.layoutSearch)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        
        val layoutSelectionBar = findViewById<View>(R.id.layoutSelectionBar)
        val tvSelectedCount = findViewById<TextView>(R.id.tvSelectedCount)
        val btnSelectAll = findViewById<View>(R.id.btnSelectAll)
        val btnDeleteSelected = findViewById<View>(R.id.btnDeleteSelected)

        val adapter = HistoryAdapter(
            onNoteClick = { trip -> showNoteDialog(trip) },
            onSelectionChanged = { count ->
                layoutSelectionBar.isVisible = count > 0
                tvSelectedCount.text = "$count seleccionados"
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        findViewById<View>(R.id.cardSummary)?.setOnClickListener {
            startActivity(Intent(this, SmartAnalystActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btnExportCsv).setOnClickListener {
            exportToCsv(adapter.currentList)
        }

        btnSelectAll.setOnClickListener {
            val currentList = adapter.currentList
            if (currentList.isNotEmpty()) {
                adapter.selectAll(currentList.map { it.id })
            }
        }

        btnDeleteSelected.setOnClickListener {
            val selectedIdsList = adapter.selectedIds.toList()
            if (selectedIdsList.isNotEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("¿Eliminar elementos seleccionados?")
                    .setMessage("Se eliminarán ${selectedIdsList.size} viajes seleccionados.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.deleteSelectedTrips(selectedIdsList)
                        adapter.clearSelection()
                        Toast.makeText(this, "Viajes eliminados", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }

        btnSearch.setOnClickListener {
            if (layoutSearch.isVisible) {
                layoutSearch.isVisible = false
                viewModel.setSearchQuery("")
                etSearch.setText("")
            } else {
                layoutSearch.isVisible = true
                etSearch.requestFocus()
            }
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipGoalsMet -> HistoryFilter.GOALS_MET
                R.id.chipRejected -> HistoryFilter.REJECTED
                R.id.chipToday -> HistoryFilter.TODAY
                else -> HistoryFilter.ALL
            }
            viewModel.setFilter(filter)
        }

        btnCalendar.setOnClickListener {
            showDatePicker()
        }

        btnClearHistory.setOnClickListener {
            showClearConfirmation()
        }

        viewModel.history.onEach { trips ->
            val prefs = (application as GoRateApplication).tripRepository.prefsRepository
            val isMiles = prefs.isMilesEnabled()
            val unitLabel = if (isMiles) "mi" else "km"

            if (trips.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                rvHistory.visibility = View.GONE
                tvTotalEarnings.text = "$0.00"
                tvTotalNet.text = "Ganancia Neta: $0.00"
                tvTotalNet.visibility = if (prefs.isNetEarningsEnabled()) View.VISIBLE else View.GONE
                tvAvgEfficiency.text = "0.00 $/$unitLabel"
            } else {
                emptyState.visibility = View.GONE
                rvHistory.visibility = View.VISIBLE
                adapter.submitList(trips)

                val total = trips.sumOf { it.price }
                val totalNet = trips.sumOf { it.netEarnings }
                val avg = if (trips.isNotEmpty()) trips.sumOf { if (isMiles) it.perKm * 1.60934 else it.perKm } / trips.size else 0.0
                tvTotalEarnings.text = String.format(Locale.US, "$%.2f", total)
                
                if (prefs.isNetEarningsEnabled()) {
                    tvTotalNet.visibility = View.VISIBLE
                    tvTotalNet.text = String.format(Locale.US, "Ganancia Neta: $%.2f", totalNet)
                } else {
                    tvTotalNet.visibility = View.GONE
                }
                
                tvAvgEfficiency.text = String.format(Locale.US, "%.2f $/$unitLabel", avg)
            }
        }.launchIn(lifecycleScope)

        findViewById<View>(R.id.btnBackHistory).setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Seleccionar Fechas")
            .build()

        datePicker.addOnPositiveButtonClickListener { range ->
            viewModel.setDateRange(range.first, range.second)
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun showClearConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Borrar Historial?")
            .setMessage("Esta acción eliminará todos los viajes guardados permanentemente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar Todo") { _, _ ->
                viewModel.clearAllHistory()
                Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showNoteDialog(trip: TripHistoryEntity) {
        val input = EditText(this).apply {
            setText(trip.note)
            hint = "Escribe un comentario sobre este viaje..."
            setPadding(48, 40, 48, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Nota del Viaje")
            .setView(input)
            .setNegativeButton("Cerrar", null)
            .setPositiveButton("Guardar") { _, _ ->
                viewModel.updateNote(trip.id, input.text.toString())
            }
            .show()
    }

    private fun exportToCsv(trips: List<TripHistoryEntity>) {
        if (trips.isEmpty()) {
            Toast.makeText(this, "No hay viajes en el historial para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        val csvBuilder = StringBuilder()
        csvBuilder.append("Fecha,Plataforma,Precio,Distancia(km),Tiempo(min),$/km,$/h,Ganancia Limpia,Recogida,Destino,Nota\n")
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (trip in trips) {
            val dateStr = dateFormat.format(java.util.Date(trip.timestamp))
            val noteClean = (trip.note ?: "").replace(",", ";").replace("\n", " ")
            val pickupClean = (trip.pickupLocation ?: "").replace(",", ";").replace("\n", " ")
            val dropoffClean = (trip.dropoffLocation ?: "").replace(",", ";").replace("\n", " ")
            csvBuilder.append("$dateStr,${trip.appOrigin},${trip.price},${trip.distanceKm},${trip.timeMin},${trip.perKm},${trip.perHour},${trip.netEarnings},\"$pickupClean\",\"$dropoffClean\",\"$noteClean\"\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de Historial GoRate")
            putExtra(Intent.EXTRA_TEXT, csvBuilder.toString())
        }
        startActivity(Intent.createChooser(intent, "Exportar Historial (CSV / Excel)"))
    }
}
