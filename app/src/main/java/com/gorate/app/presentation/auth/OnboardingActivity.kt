package com.gorate.app.presentation.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.gorate.app.databinding.ActivityOnboardingBinding
import com.gorate.app.data.repository.PreferencesRepository

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefsRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsRepository = PreferencesRepository(this)

        val adapter = OnboardingAdapter()
        binding.viewPagerOnboarding.adapter = adapter

        binding.btnNext.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current < adapter.itemCount - 1) {
                binding.viewPagerOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == adapter.itemCount - 1) {
                    binding.btnNext.text = "Comenzar"
                } else {
                    binding.btnNext.text = "Siguiente"
                }
            }
        })
    }

    private fun finishOnboarding() {
        prefsRepository.setOnboardingCompleted(true)
        finish()
    }
}
