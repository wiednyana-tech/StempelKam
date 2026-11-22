package com.wiednyana.stempelkam

import java.util.Date

data class WatermarkData(
    var time: Date = Date(),
    var hasLocation: Boolean = false,
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var altitude: Double? = null,
    var accuracy: Float = 0f,
    var address: String = "",
    var note: String = "",
    var showAddress: Boolean = true,
    var showCoords: Boolean = true,
    var showAltitude: Boolean = false,
    var showNote: Boolean = true,
)
