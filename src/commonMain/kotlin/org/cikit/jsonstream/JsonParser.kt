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
    private var bomPending = true

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
        stripBom()
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
        if (bomPending && bufLen < BOM_SIZE) {
            return endOfInput()
        }
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
            val at = bufScanPos
            val b = buffer[bufScanPos++]
            when (b) {
                '\\'.code.toByte() -> {
                    val bytesAvail = bufLen - bufScanPos
                    if (bytesAvail == 0) {
                        pushState(State.ESCAPE_1)
                        return endOfInput()
                    }
                    val escape = buffer[bufScanPos]
                    if (escape == 'u'.code.toByte()) {
                        if (bytesAvail >= 5) {
                            requireHexDigits(bufScanPos + 1)
                            bufScanPos += 5
                            continue
                        }
                        bufScanPos++
                        pushState(State.ESCAPE_4)
                        return endOfInput()
                    }
                    if (!isSimpleEscape(escape)) {
                        parseError(
                            "Invalid escape ${describeByte(bufScanPos)}",
                            bufScanPos,
                        )
                    }
                    bufScanPos++
                }
                '"'.code.toByte() -> {
                    currentStart = bufPos
                    currentEnd = bufScanPos
                    advanceBufPos()
                    popState()
                    return JsonEvent.Value
                }

                else -> {
                    if ((b.toInt() and 0xff) < 0x20) {
                        parseError(
                            "Control character ${describeByte(at)} " +
                                "must be escaped",
                            at,
                        )
                    }
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
        val escape = buffer[bufScanPos]
        if (escape == 'u'.code.toByte()) {
            if (bytesAvail >= 5) {
                requireHexDigits(bufScanPos + 1)
                bufScanPos += 5
                popState()
                return nextStr()
            }
            bufScanPos++
            setState(State.ESCAPE_4)
            return endOfInput()
        }
        if (!isSimpleEscape(escape)) {
            parseError(
                "Invalid escape ${describeByte(bufScanPos)}",
                bufScanPos,
            )
        }
        bufScanPos++
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
                    popState()
                    return plainValue(bufScanPos - 1)
                }
                '}'.code.toByte() -> {
                    singleOffset = bufBase + bufScanPos - 1
                    setState(State.SINGLE_END_OBJECT)
                    return plainValue(bufScanPos - 1)
                }
                ']'.code.toByte() -> {
                    singleOffset = bufBase + bufScanPos - 1
                    setState(State.SINGLE_END_ARRAY)
                    return plainValue(bufScanPos - 1)
                }
                ','.code.toByte() -> {
                    singleOffset = bufBase + bufScanPos - 1
                    setState(State.SINGLE_COMMA)
                    return plainValue(bufScanPos - 1)
                }
            }
        }
        if (finished) {
            popState()
            return plainValue(bufScanPos)
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

    private fun stripBom() {
        if (!bomPending || bufLen == 0) {
            return
        }
        if (buffer[0] != 0xEF.toByte()) {
            bomPending = false
            return
        }
        if (bufLen < BOM_SIZE) {
            return
        }
        bomPending = false
        if (buffer[1] == 0xBB.toByte() && buffer[2] == 0xBF.toByte()) {
            bufPos = BOM_SIZE
            bufScanPos = BOM_SIZE
        }
    }

    private fun plainValue(end: Int): JsonEvent {
        currentStart = bufPos
        currentEnd = end
        validatePlain(currentStart, currentEnd)
        advanceBufPos()
        return JsonEvent.Value
    }

    private fun validatePlain(start: Int, end: Int) {
        val first = buffer[start].toInt()
        val bad = when (first) {
            't'.code -> matchLiteral(start, end, "true")
            'f'.code -> matchLiteral(start, end, "false")
            'n'.code -> matchLiteral(start, end, "null")
            else -> matchNumber(start, end)
        }
        if (bad < 0) {
            return
        }
        if (bad >= bufLen) {
            throw JsonParseException(
                offset = bufBase + bufLen,
                message = "Unexpected end of JSON input",
            )
        }
        val text = tokenText(start, end)
        val kind = if (first == '-'.code || first in '0'.code..'9'.code) {
            "number"
        } else {
            "literal"
        }
        parseError("Invalid $kind '$text'", bad)
    }

    private fun matchLiteral(start: Int, end: Int, literal: String): Int {
        var i = start
        var j = 0
        while (j < literal.length && i < end) {
            if (buffer[i] != literal[j].code.toByte()) {
                return i
            }
            i++
            j++
        }
        if (j < literal.length) {
            return i
        }
        return if (i < end) i else -1
    }

    private fun matchNumber(start: Int, end: Int): Int {
        var i = start
        if (buffer[i] == '-'.code.toByte()) {
            i++
            if (i >= end) {
                return i
            }
        }
        when (buffer[i].toInt()) {
            '0'.code -> i++
            in '1'.code..'9'.code -> {
                i++
                while (i < end && buffer[i].toInt() in '0'.code..'9'.code) {
                    i++
                }
            }

            else -> return i
        }
        if (i < end && buffer[i] == '.'.code.toByte()) {
            i++
            if (i >= end || buffer[i].toInt() !in '0'.code..'9'.code) {
                return i
            }
            while (i < end && buffer[i].toInt() in '0'.code..'9'.code) {
                i++
            }
        }
        if (i < end && (buffer[i] == 'e'.code.toByte() ||
                buffer[i] == 'E'.code.toByte())) {
            i++
            if (i < end && (buffer[i] == '+'.code.toByte() ||
                    buffer[i] == '-'.code.toByte())) {
                i++
            }
            if (i >= end || buffer[i].toInt() !in '0'.code..'9'.code) {
                return i
            }
            while (i < end && buffer[i].toInt() in '0'.code..'9'.code) {
                i++
            }
        }
        return if (i < end) i else -1
    }

    private fun tokenText(start: Int, end: Int): String {
        val text = buffer.decodeToString(start, end)
        return if (text.length <= TOKEN_TEXT_LENGTH) {
            text
        } else {
            text.take(TOKEN_TEXT_LENGTH) + "..."
        }
    }

    private fun isSimpleEscape(b: Byte): Boolean {
        val c = b.toInt()
        return c == '"'.code || c == '\\'.code || c == '/'.code ||
            c == 'b'.code || c == 'f'.code || c == 'n'.code ||
            c == 'r'.code || c == 't'.code
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

    private companion object {
        private const val BOM_SIZE = 3
        private const val TOKEN_TEXT_LENGTH = 32
    }
}
