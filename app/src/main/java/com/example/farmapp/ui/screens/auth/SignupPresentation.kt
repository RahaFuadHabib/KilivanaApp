package com.example.farmapp.ui.screens.auth

/** Temporary signup adapter while the backend still requires a farm name.
 * This is a provisional label derived from user input, not a claimed registered farm name.
 * Keep this at the presentation boundary; the wire contract and validation stay unchanged.
 */
internal fun signupFarmName(role: SignupRole?, fullName: String): String? =
    if (role == SignupRole.FARMER && fullName.isNotBlank()) "${fullName.trim()}'s farm" else null
