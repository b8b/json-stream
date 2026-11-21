package org.cikit.jsonstream

class JsonParser {

    private var buffer = ByteArray(1024)
    private var bufLen = 0
    private var bufPos = 0
    private var bufScanPos = 0
    private var bufBase = 0L

    val totalPos: Long
        get() = bufBase + bufScanPos
    private var finished = false

    private var currentStart = -1
    private var currentEnd = -1
    private var singleOffset = -1L

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
        if (buffer.size - bufLen < length) {
            val keepFrom = if (currentStart < 0) bufPos else 0
            val keepLen = bufLen - keepFrom
            if (buffer.size - keepLen < length) {
                val newBuffer = ByteArray(
                    1 shl (Int.SIZE_BITS -
                        (keepLen + length).countLeadingZeroBits())
                )
                buffer.copyInto(newBuffer, 0, keepFrom, bufLen)
                buffer = newBuffer
            } else if (keepFrom > 0) {
                buffer.copyInto(buffer, 0, keepFrom, bufLen)
            }
            bufLen = keepLen
            if (keepFrom > 0) {
                bufScanPos -= keepFrom
                bufBase += keepFrom
                bufPos = 0
            }
        }
        data.copyInto(buffer, bufLen, offset, offset + length)
        bufLen += length
    }

    fun finish() {
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
            parseError(
                "Unexpected ${describeByte(bufScanPos)} after end of document",
                bufScanPos,
            )
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

    val currentValueOffset: Long?
        get() = if (currentStart < 0) null else bufBase + currentStart

    private fun next0(fieldName: Boolean = false): JsonEvent {
        if (!skipWhitespace()) {
            return endOfInput()
        }
        val at = bufScanPos
        val b = buffer[at]
        if (if (fieldName) b != '"'.code.toByte() else !isValueStart(b)) {
            parseError(
                "Unexpected ${describeByte(at)}, expected " +
                    (if (fieldName) "a field name" else "a value"),
                at,
            )
        }
        bufScanPos++
        return when (b) {
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
            return endOfInput()
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
                next0(fieldName = true)
            }
        }
    }

    private fun nextAfterFieldName(): JsonEvent {
        if (!skipWhitespace()) {
            return endOfInput()
        }
        return when (buffer[bufScanPos++]) {
            ':'.code.toByte() -> {
                setState(State.AFTER_FIELD_VALUE)
                pushState(State.INITIAL)
                advanceBufPos()
                next0()
            }

            else -> parseError(
                "Unexpected ${describeByte(bufScanPos - 1)}, expected ':'",
                bufScanPos - 1,
            )
        }
    }

    private fun nextAfterFieldValue(): JsonEvent {
        if (!skipWhitespace()) {
            return endOfInput()
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
                next0(fieldName = true)
            }

            else -> parseError(
                "Unexpected ${describeByte(bufScanPos - 1)}, " +
                    "expected ',' or '}'",
                bufScanPos - 1,
            )
        }
    }

    private fun nextAfterStartArray(): JsonEvent {
        if (!skipWhitespace()) {
            return endOfInput()
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
            return endOfInput()
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

            else -> parseError(
                "Unexpected ${describeByte(bufScanPos - 1)}, " +
                    "expected ',' or ']'",
                bufScanPos - 1,
            )
        }
    }

    private fun nextStr(): JsonEvent {
        while (bufScanPos < bufLen) {
            when (buffer[bufScanPos++]) {
                '\\'.code.toByte() -> {
                    val bytesAvail = bufLen - bufScanPos
                    if (bytesAvail == 0) {
                        pushState(State.ESCAPE_1)
                        return endOfInput()
                    }
                    if (buffer[bufScanPos++] == 'u'.code.toByte()) {
                        if (bytesAvail >= 5) {
                            requireHexDigits(bufScanPos)
                            bufScanPos += 4
                            continue
                        }
                        pushState(State.ESCAPE_4)
                        return endOfInput()
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
        return endOfInput()
    }

    private fun nextEscapeRequireOne() : JsonEvent {
        val bytesAvail = bufLen - bufScanPos
        if (bytesAvail == 0) {
            return endOfInput()
        }
        if (buffer[bufScanPos++] == 'u'.code.toByte()) {
            if (bytesAvail >= 5) {
                requireHexDigits(bufScanPos)
                bufScanPos += 4
                popState()
                return nextStr()
            }
            setState(State.ESCAPE_4)
            return endOfInput()
        }
        popState()
        return nextStr()
    }

    private fun nextEscapeRequireFour(): JsonEvent {
        val bytesAvail = bufLen - bufScanPos
        if (bytesAvail < 4) {
            return endOfInput()
        }
        requireHexDigits(bufScanPos)
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
                    singleOffset = bufBase + bufScanPos - 1
                    advanceBufPos()
                    setState(State.SINGLE_END_OBJECT)
                    return JsonEvent.Value
                }
                ']'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    singleOffset = bufBase + bufScanPos - 1
                    advanceBufPos()
                    setState(State.SINGLE_END_ARRAY)
                    return JsonEvent.Value
                }
                ','.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos - 1
                    singleOffset = bufBase + bufScanPos - 1
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
        if (enclosingState() != State.AFTER_FIELD_VALUE) {
            parseErrorAt("Unexpected '}'", singleOffset)
        }
        popState()
        popState()
        return JsonEvent.EndObject
    }

    private fun nextSingleEndArray(): JsonEvent {
        if (enclosingState() != State.AFTER_ARRAY_VALUE) {
            parseErrorAt("Unexpected ']'", singleOffset)
        }
        popState()
        popState()
        return JsonEvent.EndArray
    }

    private fun nextSingleComma(): JsonEvent {
        return when (enclosingState()) {
            State.AFTER_FIELD_VALUE -> {
                popState()
                setState(State.AFTER_FIELD_NAME)
                pushState(State.INITIAL)
                next0(fieldName = true)
            }
            State.AFTER_ARRAY_VALUE -> {
                popState()
                pushState(State.INITIAL)
                next0()
            }

            else -> parseErrorAt("Unexpected ','", singleOffset)
        }
    }

    private fun advanceBufPos() {
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

    private fun requireHexDigits(start: Int) {
        for (i in start until start + 4) {
            if (!isHexDigit(buffer[i])) {
                parseError("Not a hex digit ${describeByte(i)}", i)
            }
        }
    }

    private fun isHexDigit(b: Byte): Boolean {
        val c = b.toInt()
        return c in '0'.code..'9'.code ||
            c in 'a'.code..'f'.code ||
            c in 'A'.code..'F'.code
    }

    private fun describeByte(at: Int): String {
        val b = buffer[at].toInt() and 0xff
        if (b in 0x20..0x7e) {
            return "'${b.toChar()}'"
        }
        val digits = "0123456789abcdef"
        return "0x${digits[b shr 4]}${digits[b and 0xf]}"
    }

    private fun isValueStart(b: Byte): Boolean {
        val c = b.toInt()
        return c == '{'.code || c == '['.code || c == '"'.code ||
            c == '-'.code || c in '0'.code..'9'.code ||
            c == 't'.code || c == 'f'.code || c == 'n'.code
    }

    private fun endOfInput(): JsonEvent {
        if (finished) {
            throw JsonParseException(
                offset = bufBase + bufLen,
                message = "Unexpected end of JSON input",
            )
        }
        return JsonEvent.InProgress
    }

    private fun parseError(message: String, at: Int): Nothing {
        parseErrorAt(message, bufBase + at)
    }

    private fun parseErrorAt(message: String, offset: Long): Nothing {
        throw JsonParseException(offset = offset, message = message)
    }

    private fun getState(): State {
        return State.entries[sta[stSize - 1]]
    }

    private fun enclosingState(): State? {
        if (stSize < 2) {
            return null
        }
        return State.entries[sta[stSize - 2]]
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
