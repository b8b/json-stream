package org.cikit.jsonstream

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Suspending parser that feeds a [JsonParser] from [input].
 *
 * [input] is owned by the caller: it is neither closed nor cancelled here,
 * not even when parsing fails.
 *
 * [next] never reports [JsonEvent.InProgress]; it returns `null` once the
 * document has been parsed completely and throws [JsonParseException] when
 * the document is malformed or truncated. Misuse of the convenience methods
 * raises [IllegalStateException].
 */
class SuspendingJsonParser(val input: ByteReadChannel) {

    private val _p = JsonParser()
    private val chunk = ByteArray(CHUNK_SIZE)

    private suspend fun feed() {
        val n = input.readAvailable(chunk)
        if (n > 0) {
            _p.push(chunk, 0, n)
        } else if (n < 0) {
            _p.finish()
        }
    }

    /**
     * Raw JSON text of the token reported by the last [next] call, including
     * the quotes and escapes of a string.
     */
    fun currentValue() = _p.currentValue()

    /**
     * Decoded form of the token reported by the last [next] call, so
     * [JsonNull] only ever represents the JSON literal `null`.
     *
     * @throws IllegalStateException if the last call did not report a
     * [JsonEvent.Value].
     * @throws JsonParseException if the token is not a valid JSON value.
     */
    fun currentJsonElement(): JsonElement {
        val text = _p.currentValue()
            ?: throw IllegalStateException("no current value")
        return decode(text)
    }

    /**
     * Returns the next event, or `null` at the end of a valid document.
     *
     * @throws JsonParseException if the document is malformed or truncated.
     */
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

    /**
     * Returns the next event and fails unless it is of type [T].
     *
     * @throws IllegalStateException if the next event has another type.
     */
    suspend inline fun <reified T: JsonEvent> expect(): T {
        val ev = next()
        check(ev is T) {
            "unexpected json event '$ev': expected ${T::class}"
        }
        return ev
    }

    /**
     * Skips the value at the current position, including everything nested
     * below a [JsonEvent.StartObject] or [JsonEvent.StartArray]. The parser
     * is positioned after the skipped value on return.
     *
     * A value must be next; calling this at a field name or at the end of a
     * container skips the wrong bytes.
     *
     * @throws IllegalStateException if there is no value left to skip.
     * @throws JsonParseException if the document is malformed.
     */
    suspend fun skipJsonElement() {
        when (next()) {
            is JsonEvent.StartObject, is JsonEvent.StartArray -> {
                var depth = 1
                while (depth > 0) {
                    when (val ev = next()) {
                        is JsonEvent.StartObject,
                        is JsonEvent.StartArray -> depth++

                        is JsonEvent.EndObject,
                        is JsonEvent.EndArray -> depth--

                        is JsonEvent.Value -> {}

                        null -> throw IllegalStateException(
                            "no value left to skip"
                        )

                        else -> throw JsonParseException(
                            _p.totalPos,
                            "unexpected json event '$ev'",
                        )
                    }
                }
            }
            is JsonEvent.Value -> {}
            else -> throw IllegalStateException("no value left to skip")
        }
    }

    /**
     * Returns the value at the current position as a [JsonElement], or `null`
     * if the next event is not a value, for example at the end of an array.
     *
     * A value must be next; calling this at a field name interprets the name
     * as a value.
     *
     * @throws JsonParseException if the document is malformed.
     */
    suspend fun nextJsonElement(): JsonElement? {
        return when (next()) {
            is JsonEvent.StartObject -> parseObject()
            is JsonEvent.StartArray -> parseArray()
            is JsonEvent.Value -> currentJsonElement()
            else -> null
        }
    }

    private fun decode(text: String): JsonElement {
        return try {
            Json.decodeFromString<JsonElement>(text)
        } catch (e: SerializationException) {
            throw JsonParseException(
                offset = _p.currentValueOffset ?: 0L,
                message = "not a JSON value: ${abbreviate(text)}",
            )
        }
    }

    private fun fieldName(): String {
        val element = currentJsonElement()
        if (element !is JsonPrimitive || !element.isString) {
            val text = _p.currentValue() ?: ""
            throw JsonParseException(
                offset = _p.currentValueOffset ?: 0L,
                message = "not a JSON field name: ${abbreviate(text)}",
            )
        }
        return element.content
    }

    private fun abbreviate(text: String): String {
        return if (text.length <= ABBREVIATE_LENGTH) {
            text
        } else {
            text.take(ABBREVIATE_LENGTH) + "..."
        }
    }

    private suspend fun parseArray(): JsonArray = buildJsonArray {
        while (true) {
            when (val ev = next()) {
                is JsonEvent.StartObject -> add(parseObject())
                is JsonEvent.StartArray -> add(parseArray())
                is JsonEvent.Value -> add(currentJsonElement())
                is JsonEvent.EndArray -> break
                else -> throw JsonParseException(
                    _p.totalPos,
                    "unexpected json event '$ev' while parsing array element",
                )
            }
        }
    }

    private suspend fun parseObject(): JsonObject = buildJsonObject {
        while (true) {
            val fieldName = when (val ev = next()) {
                is JsonEvent.Value -> fieldName()

                is JsonEvent.EndObject -> break
                else -> throw JsonParseException(
                    _p.totalPos,
                    "unexpected json event '$ev' while parsing field name",
                )
            }
            when (val ev = next()) {
                is JsonEvent.StartObject -> put(fieldName, parseObject())
                is JsonEvent.StartArray -> put(fieldName, parseArray())
                is JsonEvent.Value -> put(fieldName, currentJsonElement())
                else -> throw JsonParseException(
                    _p.totalPos,
                    "unexpected json event '$ev' while parsing value",
                )
            }
        }
    }

    private companion object {
        private const val CHUNK_SIZE = 8192
        private const val ABBREVIATE_LENGTH = 32
    }
}
