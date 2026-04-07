package org.cikit.jsonstream

class JsonParser {

    private var buffer = ByteArray(1024)
    private var bufLen = 0
    private var bufPos = 0
    private var bufScanPos = 0
    var totalPos = 0L
        private set
    private var finished = false

    private var currentStart = -1
    private var currentEnd = -1

    private enum class State {
        INITIAL,
        AFTER_START_OBJECT,
        AFTER_FIELD_NAME,
        AFTER_FIELD_VALUE,
        AFTER_START_ARRAY,
        AFTER_ARRAY_VALUE,
        STRING,
        ESCAPE_1,
        ESCAPE_4,
        PLAIN,
        SINGLE_END_OBJECT,
        SINGLE_END_ARRAY,
        SINGLE_COMMA
    }

    private var sta = IntArray(8)
    private var stSize = 0

    init {
        pushState(State.INITIAL)
    }

    fun push(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        val bufAvail = buffer.size - bufLen
        if (bufAvail >= length) {
            data.copyInto(buffer, bufLen, offset, offset + length)
            bufLen += length
        } else {
            val bufAvailMax = bufAvail + bufPos
            if (bufAvailMax < length) {
                val newBufLen = bufLen - bufPos + length
                val newBuffer = ByteArray(
                    1 shl (Int.SIZE_BITS - newBufLen.countLeadingZeroBits())
                )
                buffer.copyInto(newBuffer, 0, bufPos, bufLen)
                data.copyInto(
                    newBuffer,
                    bufLen - bufPos,
                    offset,
                    offset + length
                )
                buffer = newBuffer
                bufLen = newBufLen
                bufScanPos -= bufPos
                totalPos += bufPos
                bufPos = 0
            } else {
                buffer.copyInto(buffer, 0, bufPos, bufLen)
                data.copyInto(buffer, bufLen - bufPos, offset, offset + length)
                bufLen = bufLen - bufPos + length
                bufScanPos -= bufPos
                totalPos += bufPos
                bufPos = 0
            }
        }
    }

    fun finish() {
        require(!finished)
        finished = true
    }

    fun next(): JsonEvent? {
        currentStart = -1
        currentEnd = -1
        if (stSize == 0) {
            if (!skipWhitespace()) {
                if (!finished) {
                    return JsonEvent.InProgress
                }
                return null
            }
            parseError("trailing garbage")
        }
        return when (getState()) {
            State.INITIAL -> next0()
            State.AFTER_START_OBJECT -> nextAfterStartObject()
            State.AFTER_FIELD_NAME -> nextAfterFieldName()
            State.AFTER_FIELD_VALUE -> nextAfterFieldValue()
            State.AFTER_START_ARRAY -> nextAfterStartArray()
            State.AFTER_ARRAY_VALUE -> nextAfterArrayValue()
            State.STRING -> nextStr()
            State.ESCAPE_1 -> nextEscapeRequireOne()
            State.ESCAPE_4 -> nextEscapeRequireFour()
            State.PLAIN -> nextPlain()
            State.SINGLE_END_OBJECT -> nextSingleEndObject()
            State.SINGLE_END_ARRAY -> nextSingleEndArray()
            State.SINGLE_COMMA -> nextSingleComma()
        }
    }

    fun currentValue(): String? {
        if (currentStart < 0) {
            return null
        }
        if (currentEnd > currentStart) {
            return buffer.decodeToString(currentStart, currentEnd)
        }
        return ""
    }

    private fun next0(): JsonEvent {
        if (!skipWhitespace()) {
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            '{'.code.toByte() -> {
                setState(State.AFTER_START_OBJECT)
                advanceBufPos()
                JsonEvent.StartObject
            }
            '['.code.toByte() -> {
                setState(State.AFTER_START_ARRAY)
                advanceBufPos()
                JsonEvent.StartArray
            }
            '"'.code.toByte() -> {
                setState(State.STRING)
                nextStr()
            }

            else -> {
                setState(State.PLAIN)
                nextPlain()
            }
        }
    }

    private fun nextAfterStartObject(): JsonEvent {
        if (!skipWhitespace()) {
            require(!finished)
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            '}'.code.toByte() -> {
                popState()
                advanceBufPos()
                JsonEvent.EndObject
            }

            else -> {
                setState(State.AFTER_FIELD_NAME)
                pushState(State.INITIAL)
                bufScanPos--
                next0()
            }
        }
    }

    private fun nextAfterFieldName(): JsonEvent {
        if (!skipWhitespace()) {
            require(!finished)
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            ':'.code.toByte() -> {
                setState(State.AFTER_FIELD_VALUE)
                pushState(State.INITIAL)
                advanceBufPos()
                next0()
            }

            else -> parseError("Expected ':' in object")
        }
    }

    private fun nextAfterFieldValue(): JsonEvent {
        if (!skipWhitespace()) {
            require(!finished)
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            '}'.code.toByte() -> {
                popState()
                advanceBufPos()
                JsonEvent.EndObject
            }
            ','.code.toByte() -> {
                setState(State.AFTER_FIELD_NAME)
                pushState(State.INITIAL)
                advanceBufPos()
                next0()
            }

            else -> parseError("Expected ',' or '}' in object")
        }
    }

    private fun nextAfterStartArray(): JsonEvent {
        if (!skipWhitespace()) {
            require(!finished)
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            ']'.code.toByte() -> {
                popState()
                advanceBufPos()
                JsonEvent.EndArray
            }

            else -> {
                setState(State.AFTER_ARRAY_VALUE)
                pushState(State.INITIAL)
                bufScanPos--
                next0()
            }
        }
    }

    private fun nextAfterArrayValue(): JsonEvent {
        if (!skipWhitespace()) {
            require(!finished)
            return JsonEvent.InProgress
        }
        return when (buffer[bufScanPos++]) {
            ']'.code.toByte() -> {
                popState()
                advanceBufPos()
                JsonEvent.EndArray
            }
            ','.code.toByte() -> {
                pushState(State.INITIAL)
                next0()
            }

            else -> parseError("Expected ',' or ']' in array")
        }
    }

    private fun nextStr(): JsonEvent {
        while (bufScanPos < bufLen) {
            when (buffer[bufScanPos++]) {
                '\\'.code.toByte() -> {
                    val bytesAvail = bufLen - bufScanPos
                    if (bytesAvail == 0) {
                        pushState(State.ESCAPE_1)
                        require(!finished)
                        return JsonEvent.InProgress
                    }
                    if (buffer[bufScanPos++] == 'u'.code.toByte()) {
                        if (bytesAvail >= 5) {
                            bufScanPos += 4
                            continue
                        }
                        pushState(State.ESCAPE_4)
                        require(!finished)
                        return JsonEvent.InProgress
                    }
                }
                '"'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos
                    advanceBufPos()
                    popState()
                    return JsonEvent.Value
                }
            }
        }
        require(!finished) { "Unexpected end of stream" }
        return JsonEvent.InProgress
    }

    private fun nextEscapeRequireOne() : JsonEvent {
        val bytesAvail = bufLen - bufScanPos
        if (bytesAvail == 0) {
            require(!finished)
            return JsonEvent.InProgress
        }
        if (buffer[bufScanPos++] == 'u'.code.toByte()) {
            if (bytesAvail >= 5) {
                bufScanPos += 4
                popState()
                return nextStr()
            }
            pushState(State.ESCAPE_4)
            require(!finished)
            return JsonEvent.InProgress
        }
        popState()
        return nextStr()
    }

    private fun nextEscapeRequireFour(): JsonEvent {
        val bytesAvail = bufLen - bufScanPos
        if (bytesAvail < 4) {
            require(!finished)
            return JsonEvent.InProgress
        }
        bufScanPos += 4
        popState()
        return nextStr()
    }

    private fun nextPlain(): JsonEvent {
        while (bufScanPos < bufLen) {
            when (buffer[bufScanPos++]) {
                ' '.code.toByte(),
                '\t'.code.toByte(),
                '\r'.code.toByte(),
                '\n'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    advanceBufPos()
                    popState()
                    return JsonEvent.Value
                }
                '}'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    advanceBufPos()
                    setState(State.SINGLE_END_OBJECT)
                    return JsonEvent.Value
                }
                ']'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    advanceBufPos()
                    setState(State.SINGLE_END_ARRAY)
                    return JsonEvent.Value
                }
                ','.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    advanceBufPos()
                    setState(State.SINGLE_COMMA)
                    return JsonEvent.Value
                }
            }
        }
        if (finished) {
            currentStart = bufPos
            currentEnd = bufScanPos
            advanceBufPos()
            popState()
            return JsonEvent.Value
        }
        return JsonEvent.InProgress
    }

    private fun nextSingleEndObject(): JsonEvent {
        popState()
        when (getState()) {
            State.AFTER_FIELD_VALUE -> popState()

            else -> parseError("Unexpected token '}'")
        }
        return JsonEvent.EndObject
    }

    private fun nextSingleEndArray(): JsonEvent {
        popState()
        when (getState()) {
            State.AFTER_ARRAY_VALUE -> popState()

            else -> parseError("Unexpected token ']'")
        }
        return JsonEvent.EndArray
    }

    private fun nextSingleComma(): JsonEvent {
        popState()
        return when (getState()) {
            State.AFTER_FIELD_VALUE -> {
                setState(State.AFTER_FIELD_NAME)
                pushState(State.INITIAL)
                next0()
            }
            State.AFTER_ARRAY_VALUE -> {
                pushState(State.INITIAL)
                next0()
            }

            else -> parseError("Unexpected token ','")
        }
    }

    private fun advanceBufPos() {
        totalPos += bufScanPos - bufPos
        bufPos = bufScanPos
    }

    private fun skipWhitespace(): Boolean {
        while (bufScanPos < bufLen) {
            when (buffer[bufScanPos]) {
                ' '.code.toByte(),
                '\t'.code.toByte(),
                '\r'.code.toByte(),
                '\n'.code.toByte() -> bufScanPos++
                else -> {
                    advanceBufPos()
                    return true
                }
            }
        }
        advanceBufPos()
        return false
    }

    private fun parseError(message: String): Nothing {
        throw JsonParseException(
            offset = totalPos + (bufScanPos - bufPos),
            message = message,
        )
    }

    private fun getState(): State {
        return State.entries[sta[stSize - 1]]
    }

    private fun setState(state: State) {
        sta[stSize - 1] = state.ordinal
    }

    private fun pushState(state: State) {
        if (stSize == sta.size) {
            sta = sta.copyOf(stSize shl 1)
        }
        sta[stSize++] = state.ordinal
    }

    private fun popState() {
        stSize--
    }
}
