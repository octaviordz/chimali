package com.chimali.fido2

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxState

@OptIn(ExperimentalMaterial3Api::class)
fun testDirection(state: SwipeToDismissBoxState) {
    val dir = state.dismissDirection
}
