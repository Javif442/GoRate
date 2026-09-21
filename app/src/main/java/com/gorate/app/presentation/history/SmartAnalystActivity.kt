package com.gorate.app.presentation.history

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class SmartAnalystActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_analyst)

        findViewById<ImageButton>(R.id.btnBackAnalyst).setOnClickListener { finish() }

        loadAnalytics()
    }

    private fun loadAnalytics() {
        val app = application as GoRateApplication
        lifecycleScope.launch {
            val trips = app.tripRepository.getAllHistory().first()
            val prefs = app.tripRepository.prefsRepository
            val minKm = prefs.getMinPerKm()

            val totalTrips = trips.size
            val totalEarnings = trips.sumOf { it.price }
            val avgKm = if (totalTrips > 0) trips.sumOf { it.perKm } / totalTrips else 0.0
            val avgHr = if (totalTrips > 0) trips.sumOf { it.perHour } / totalTrips else 0.0
            val goalsMet = trips.count { it.perKm >= minKm }
            val goalsMetPct = if (totalTrips > 0) (goalsMet * 100) / totalTrips else 0

            val diagnosisText = if (totalTrips == 0) {
                "Aún no tienes viajes registrados en el historial. Empieza a capturar ofertas con GoRate activo para que el Analista IA pueda evaluar tu rendimiento."
            } else {
                "• Has analizado un total de $totalTrips solicitudes.\n\n" +
                "• Tu ganancia bruta acumulada es de $${String.format(Locale.US, "%.2f", totalEarnings)}.\n\n" +
                "• Tu eficiencia promedio se mantiene en ${String.format(Locale.US, "%.2f", avgKm)} $/km y ${String.format(Locale.US, "%.2f", avgHr)} $/h.\n\n" +
                "• El $goalsMetPct% de los viajes analizados cumplieron o superaron tu meta mínima ($minKm $/km)."
            }

            val adviceText = when {
                totalTrips == 0 -> "Mantén el escáner encendido durante tus horas de trabajo para recopilar datos estadísticos precisos."
                goalsMetPct < 40 -> "⚠️ Alerta de Rentabilidad: Estás aceptando o recibiendo muchas ofertas por debajo de tu mínimo ($minKm $/km). Te sugerimos ser más exigente y rechazar viajes largos con baja tarifa."
                goalsMetPct >= 70 -> "🌟 ¡Excelente Desempeño! Estás cazando ofertas altamente rentables. Tu estrategia de selección de viajes está optimizando al máximo el desgaste de tu vehículo y combustible."
                else -> "⚖️ Rendimiento Estable: Vas por buen camino, pero puedes mejorar si priorizas viajes cortos en horas pico y evitas zonas de alto tráfico con baja retribución."
            }

            findViewById<TextView>(R.id.tvAnalystDiagnosis).text = diagnosisText
            findViewById<TextView>(R.id.tvAnalystAdvice).text = adviceText
        }
    }
}
