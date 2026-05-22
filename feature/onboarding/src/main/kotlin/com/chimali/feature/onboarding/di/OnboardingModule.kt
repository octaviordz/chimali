package com.chimali.feature.onboarding.di

import com.chimali.feature.onboarding.presentation.viewmodel.OnboardingViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val onboardingModule =
    module {
        viewModel { OnboardingViewModel(get()) }
    }
