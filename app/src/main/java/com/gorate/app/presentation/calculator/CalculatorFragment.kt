package com.gorate.app.presentation.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import com.gorate.app.databinding.FragmentCalculatorBinding
import com.gorate.app.domain.model.TripData
import com.gorate.app.domain.usecase.CalculateProfitUseCase
import java.util.Locale

class CalculatorFragment : Fragment() {

    private var _binding: FragmentCalculatorBinding? = null
    private val binding get() = _binding!!
    private val calculateProfitUseCase = CalculateProfitUseCase()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCalculate.setOnClickListener {
            calculateTrip()
        }
    }

    private fun calculateTrip() {
        val priceStr = binding.etCalcPrice.text.toString().trim()
        val distStr = binding.etCalcDistance.text.toString().trim()
        val timeStr = binding.etCalcTime.text.toString().trim()

        val price = priceStr.toDoubleOrNull()
        val dist = distStr.toDoubleOrNull()
        val time = timeStr.toDoubleOrNull()

        if (price == null || dist == null || time == null || price <= 0 || dist <= 0 || time <= 0) {
            Toast.makeText(requireContext(), "Por favor, ingresa valores válidos mayores a cero.", Toast.LENGTH_SHORT).show()
            return
        }

        val app = requireActivity().application as GoRateApplication
        val prefs = app.tripRepository.prefsRepository
        val fuelCost = prefs.getFuelCostPerKm()
        val isMiles = prefs.isMilesEnabled()

        val tripData = TripData(price = price, distanceKm = if (isMiles) dist * 1.60934 else dist, timeMin = time)
        val result = calculateProfitUseCase(tripData, fuelCost)

        val displayDist = if (isMiles) result.distanceKm / 1.60934 else result.distanceKm
        val displayPerKm = if (isMiles) result.perKm * 1.60934 else result.perKm
        val unitLabel = if (isMiles) "mi" else "km"

        val minKm = prefs.getMinPerKm()
        val excKm = prefs.getExcPerKm()

        val recommendation = when {
            displayPerKm >= excKm -> "✔ ACEPTAR (Excelente)"
            displayPerKm >= minKm -> "⚠ CONSIDERAR (Aceptable)"
            else -> "✖ RECHAZAR (No rentable)"
        }

        val colorRes = when {
            displayPerKm >= excKm -> R.color.status_green
            displayPerKm >= minKm -> R.color.status_yellow
            else -> R.color.status_red
        }

        binding.cardCalcResult.visibility = View.VISIBLE
        binding.tvCalcRecommendation.text = recommendation
        binding.tvCalcRecommendation.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        binding.cardCalcResult.setStrokeColor(ContextCompat.getColor(requireContext(), colorRes))

        val isMinutes = prefs.isMinutesEnabled()
        val timeRate = if (isMinutes) result.perHour / 60.0 else result.perHour
        val timeUnit = if (isMinutes) "min" else "h"

        binding.tvCalcPerKm.text = String.format(Locale.US, "%.2f $/$unitLabel", displayPerKm)
        binding.tvCalcPerHour.text = String.format(Locale.US, "%.2f $/$timeUnit", timeRate)

        if (prefs.isNetEarningsEnabled()) {
            binding.tvCalcNet.visibility = View.VISIBLE
            binding.tvCalcNet.text = String.format(Locale.US, "Ganancia Limpia: $%.2f", result.netEarnings)
        } else {
            binding.tvCalcNet.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
