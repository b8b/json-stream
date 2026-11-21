package org.cikit.jsonstream

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuspendingParserTest {

    private fun parser(json: String) =
        SuspendingJsonParser(ByteReadChannel(json.encodeToByteArray()))

    @Test
    fun testCurrentJsonElementWithoutTokenFails() = runBlocking {
        val p = parser("[]")
        assertEquals(JsonEvent.StartArray, p.next())
        val ex = assertFailsWith<IllegalStateException> {
            p.currentJsonElement()
        }
        assertEquals("no current value", ex.message)
    }

    @Test
    fun testCurrentJsonElementOfNullLiteral() = runBlocking {
        val p = parser("null")
        assertEquals(JsonEvent.Value, p.next())
        assertEquals(JsonNull, p.currentJsonElement())
    }

    @Test
    fun testInvalidStringEscapeRaisesParseExceptionWithOffset() = runBlocking {
        val p = parser("""["a\qb"]""")
        assertEquals(JsonEvent.StartArray, p.next())
        assertEquals(JsonEvent.Value, p.next())
        assertEquals(""""a\qb"""", p.currentValue())
        val ex = assertFailsWith<JsonParseException> {
            p.currentJsonElement()
        }
        assertEquals(1L, ex.offset)
        assertEquals(
            """not a JSON value: "a\qb" at byte offset 1""",
            ex.message,
        )
    }

    @Test
    fun testEscapedFieldNamesAreDecoded() = runBlocking {
        val p = parser("""{"a\u0041":1}""")
        assertEquals(JsonEvent.StartObject, p.next())
        assertEquals(JsonEvent.Value, p.next())
        assertEquals("aA", p.currentJsonElement().jsonPrimitive.content)
    }

    @Test
    fun testNextJsonElementRoundTrip() = runBlocking {
        val doc = """{"a":[1,{"b":null}],"c":"d"}"""
        val p = parser(doc)
        assertEquals(doc, p.nextJsonElement().toString())
        assertNull(p.nextJsonElement())
    }

    @Test
    fun testSkipJsonElementConsumesNestedValue() = runBlocking {
        val p = parser("""{"a":[1,{"b":[2,3]}],"c":4}""")
        assertEquals(JsonEvent.StartObject, p.next())
        assertEquals(JsonEvent.Value, p.next())
        p.skipJsonElement()
        assertEquals(JsonEvent.Value, p.next())
        assertEquals("\"c\"", p.currentValue())
        assertEquals(JsonEvent.Value, p.next())
        assertEquals("4", p.currentValue())
        assertEquals(JsonEvent.EndObject, p.next())
        assertNull(p.next())
    }

    @Test
    fun testSkipJsonElementWithoutValueFails() = runBlocking {
        val p = parser("[]")
        assertEquals(JsonEvent.StartArray, p.next())
        val ex = assertFailsWith<IllegalStateException> {
            p.skipJsonElement()
        }
        assertEquals("no value left to skip", ex.message)
    }

    @Test
    fun testExpectRejectsOtherEvents() = runBlocking {
        val p = parser("[]")
        assertEquals(JsonEvent.StartArray, p.expect<JsonEvent.StartArray>())
        val ex = assertFailsWith<IllegalStateException> {
            p.expect<JsonEvent.StartObject>()
        }
        val message = ex.message!!
        assertTrue(message.contains("unexpected json event 'EndArray'"), message)
        assertTrue(message.contains("StartObject"), message)
    }

    @Test
    fun testTruncatedBodyRaisesParseException() = runBlocking {
        val p = parser("""{"a":[1""")
        assertEquals(JsonEvent.StartObject, p.next())
        assertEquals(JsonEvent.Value, p.next())
        assertEquals(JsonEvent.StartArray, p.next())
        assertEquals(JsonEvent.Value, p.next())
        val ex = assertFailsWith<JsonParseException> { p.next() }
        assertEquals(
            "Unexpected end of JSON input at byte offset 7",
            ex.message,
        )
    }

    @Test
    fun testEmptyBodyRaisesParseException() = runBlocking {
        val p = parser("")
        val ex = assertFailsWith<JsonParseException> { p.next() }
        assertEquals(
            "Unexpected end of JSON input at byte offset 0",
            ex.message,
        )
    }
}
