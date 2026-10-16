package com.example.farmapp.network.canonical

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.math.BigDecimal

/**
 * Opt-in codec for canonical DTOs only. Not installed in either active Retrofit client.
 * Read integer lexemes directly: never pass IDs through Double, and reject fractional/overflow input.
 */
object CanonicalJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Long::class.javaObjectType, ExactLongAdapter().nullSafe())
        .registerTypeAdapter(Long::class.javaPrimitiveType, ExactLongAdapter().nullSafe())
        .registerTypeAdapter(Int::class.javaObjectType, ExactIntAdapter().nullSafe())
        .registerTypeAdapter(Int::class.javaPrimitiveType, ExactIntAdapter().nullSafe())
        .create()
}

private fun JsonReader.exactNumber(): BigDecimal {
    if (peek() != JsonToken.NUMBER) throw JsonParseException("Expected a canonical integer number")
    return try {
        BigDecimal(nextString())
    } catch (_: NumberFormatException) {
        throw JsonParseException("Invalid canonical integer number")
    }
}

private class ExactLongAdapter : TypeAdapter<Long>() {
    override fun read(input: JsonReader): Long = try {
        input.exactNumber().longValueExact()
    } catch (_: ArithmeticException) {
        throw JsonParseException("Canonical integer is fractional or outside Long range")
    }

    override fun write(output: JsonWriter, value: Long?) { output.value(value) }
}

private class ExactIntAdapter : TypeAdapter<Int>() {
    override fun read(input: JsonReader): Int = try {
        input.exactNumber().intValueExact()
    } catch (_: ArithmeticException) {
        throw JsonParseException("Canonical integer is fractional or outside Int range")
    }

    override fun write(output: JsonWriter, value: Int?) { output.value(value) }
}
