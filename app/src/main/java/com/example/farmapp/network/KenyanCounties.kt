package com.example.farmapp.network

/** Canonical county spellings from the read-only backend KenyaCounty source. No location permission needed. */
object KenyanCounties {
    val all = listOf(
        "Mombasa", "Kwale", "Kilifi", "Tana River", "Lamu", "Taita-Taveta",
        "Garissa", "Wajir", "Mandera", "Marsabit", "Isiolo", "Meru",
        "Tharaka-Nithi", "Embu", "Kitui", "Machakos", "Makueni",
        "Nyandarua", "Nyeri", "Kirinyaga", "Murang'a", "Kiambu",
        "Turkana", "West Pokot", "Samburu", "Trans Nzoia", "Uasin Gishu",
        "Elgeyo-Marakwet", "Nandi", "Baringo", "Laikipia", "Nakuru", "Narok",
        "Kajiado", "Kericho", "Bomet", "Kakamega", "Vihiga", "Bungoma", "Busia",
        "Siaya", "Kisumu", "Homa Bay", "Migori", "Kisii", "Nyamira", "Nairobi City"
    )
    private fun key(value: String) = value.trim().lowercase(java.util.Locale.ROOT)
        .replace("'", "").replace("’", "").replace(Regex("[^a-z0-9]+"), " ").trim()
    fun normalize(value: String): String? = all.firstOrNull { key(it) == key(value) }
        ?: "Nairobi City".takeIf { key(value) == "nairobi" }
}
