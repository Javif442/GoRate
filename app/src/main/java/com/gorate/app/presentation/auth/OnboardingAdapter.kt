package com.gorate.app.presentation.auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gorate.app.R

class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    private val titles = arrayOf(
        "Bienvenido a GoRate Pro",
        "Semáforo Inteligente de Viajes",
        "Operación Segura en Segundo Plano"
    )

    private val descriptions = arrayOf(
        "Tu copiloto inteligente definitivo para analizar ofertas de Uber, DiDi e InDrive en tiempo real.",
        "Evalúa al instante si un viaje es Verde (Aceptar), Amarillo (Considerar) o Rojo (Rechazar) según tus metas personales de rentabilidad.",
        "Activa la Superposición y la Accesibilidad para que GoRate trabaje por ti de forma invisible y segura mientras manejas."
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSlideTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvSlideDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvTitle.text = titles[position]
        holder.tvDesc.text = descriptions[position]
    }

    override fun getItemCount(): Int = titles.size
}
