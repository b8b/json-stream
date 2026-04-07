package org.cikit.jsonstream

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class SuspendingJsonParser(val input: ByteReadChannel) {

    private val _p = JsonParser()

    private suspend fun feed() {
        if (input.availableForRead > 0 || input.awaitContent()) {
            input.readAvailable(1) { buffer ->
                val data = buffer.readBytes()
                _p.push(data, 0, data.size)
                data.size
            }
        } else {
            _p.finish()
        }
    }

    fun currentValue() = _p.currentValue()

    fun currentJsonElement() = _p.currentValue()
        ?.let { Json.decodeFromString<JsonElement>(it) }
        ?: JsonNull

    suspend fun next(): JsonEvent? {
        while (true) {
            val ev = _p.next() ?: return null
            if (ev == JsonEvent.InProgress) {
                feed()
            } else {
                return ev
            }
        }
    }

    suspend inline fun <reified T: JsonEvent> expect(): T {
        val ev = next()
        require(ev is T) {
            "unexpected json event '$ev': expected ${T::class}"
        }
        return ev
    }

    suspend fun skipJsonElement(): Boolean {
        return when (next()) {
            is JsonEvent.StartObject, is JsonEvent.StartArray -> {
                var depth = 1
                while (depth > 0) {
                    when (next()) {
                        is JsonEvent.StartObject,
                        is JsonEvent.StartArray -> depth++
                        is JsonEvent.EndObject,
                        is JsonEvent.EndArray -> depth--
                        is JsonEvent.Value -> {}
                        else -> return false
                    }
                }
                true
            }
            is JsonEvent.Value -> true
            else -> false
        }
    }

    suspend fun nextJsonElement(): JsonElement? {
        return when (next()) {
            is JsonEvent.StartObject -> parseObject()
            is JsonEvent.StartArray -> parseArray()
            is JsonEvent.Value -> currentJsonElement()
            else -> null
        }
    }

    private suspend fun parseArray(): JsonArray = buildJsonArray {
        while (true) {
            when (val ev = next()) {
                is JsonEvent.StartObject -> add(parseObject())
                is JsonEvent.StartArray -> add(parseArray())
                is JsonEvent.Value -> add(currentJsonElement())
                is JsonEvent.EndArray -> break
                else -> error(
                    "unexpected json event '$ev' while parsing array element"
                )
            }
        }
    }

    private suspend fun parseObject(): JsonObject = buildJsonObject {
        while (true) {
            val fieldName = when (val ev = next()) {
                is JsonEvent.Value -> {
                    val elm = currentJsonElement()
                    require(elm is JsonPrimitive && elm.isString)
                    elm.content
                }

                is JsonEvent.EndObject -> break
                else -> error(
                    "unexpected json event '$ev' while parsing field name"
                )
            }
            when (val ev = next()) {
                is JsonEvent.StartObject -> put(fieldName, parseObject())
                is JsonEvent.StartArray -> put(fieldName, parseArray())
                is JsonEvent.Value -> put(fieldName, currentJsonElement())
                else -> error(
                    "unexpected json event '$ev' while parsing value"
                )
            }
        }
    }
}
