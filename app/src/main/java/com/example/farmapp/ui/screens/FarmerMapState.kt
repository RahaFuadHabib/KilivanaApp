package com.example.farmapp.ui.screens

internal enum class MapPermission { DENIED, APPROXIMATE, PRECISE }
internal fun mapPermission(coarse: Boolean, fine: Boolean): MapPermission = when {
    fine -> MapPermission.PRECISE
    coarse -> MapPermission.APPROXIMATE
    else -> MapPermission.DENIED
}
internal enum class RecenterAction { REQUEST_PERMISSION, OPEN_LOCATION_SETTINGS, WAIT_FOR_FIX, CENTER }
internal fun recenterAction(permission: MapPermission, servicesEnabled: Boolean, hasFix: Boolean): RecenterAction = when {
    permission == MapPermission.DENIED -> RecenterAction.REQUEST_PERMISSION
    !servicesEnabled -> RecenterAction.OPEN_LOCATION_SETTINGS
    !hasFix -> RecenterAction.WAIT_FOR_FIX
    else -> RecenterAction.CENTER
}
