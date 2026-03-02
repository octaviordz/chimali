package com.chimali.fido2.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chimali.fido2.presentation.ui.RegistrationPromptScreen

/**
 * T067 — Navigation graph for the FIDO2 Registration flow.
 *
 * ### Destinations
 * | Route                   | Screen                          |
 * |-------------------------|---------------------------------|
 * | `fido2/register`        | [RegistrationPromptScreen]      |
 * | `fido2/register/success`| Success confirmation (inline)   |
 *
 * The registration flow is a nested graph embedded inside the host app's
 * NavHost. Entry via `deeplink://chimali/fido2/register` when the HID
 * transport receives a CTAP2 MakeCredential request.
 */
object Fido2Destinations {
    const val REGISTRATION_ROUTE = "fido2/register"
    const val REGISTRATION_SUCCESS_ROUTE = "fido2/register/success"
}

/**
 * The FIDO2 registration navigation host. Typically embedded as a nested graph
 * inside the host application NavHost.
 *
 * @param onRegistrationComplete Called with the credential ID when registration succeeds.
 * @param onRegistrationCancelled Called when user dismisses without registering.
 * @param navController NavHostController; defaults to a remembered one for standalone use.
 * @param startDestination Initial route; defaults to the registration prompt.
 */
@Composable
fun Fido2RegistrationNavGraph(
    onRegistrationComplete: (credentialId: String) -> Unit,
    onRegistrationCancelled: () -> Unit,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Fido2Destinations.REGISTRATION_ROUTE,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {
        composable(Fido2Destinations.REGISTRATION_ROUTE) {
            RegistrationPromptScreen(
                onSuccess = { credentialId ->
                    onRegistrationComplete(credentialId)
                },
                onCancel = {
                    onRegistrationCancelled()
                }
            )
        }
    }
}
