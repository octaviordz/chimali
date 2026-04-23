package com.chimali.fido2.di

/**
 * FIDO2 ViewModel DI registration — Koin Annotations style.
 *
 * ViewModels are annotated with [@KoinViewModel][org.koin.core.annotation.KoinViewModel]
 * directly on their class declarations and are discovered automatically by
 * [Fido2Module]'s [@ComponentScan][org.koin.core.annotation.ComponentScan] over the
 * `com.chimali.fido2` package.
 *
 * This file is kept as an architectural documentation marker; no DSL module is needed.
 *
 * ViewModels included:
 *  - [com.chimali.fido2.presentation.viewmodel.RegistrationPromptViewModel]
 *  - [com.chimali.fido2.presentation.viewmodel.AuthenticationPromptViewModel]
 *  - [com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel]
 *  - [com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel]
 *  - [com.chimali.fido2.presentation.viewmodel.DevToolsViewModel]
 *  - [com.chimali.fido2.presentation.management.CredentialManagementViewModel]
 */
