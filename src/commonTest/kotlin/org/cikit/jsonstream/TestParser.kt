package org.cikit.jsonstream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TestParser {

    val sampleInput = """{"status":"success","data":{"resultType":"matrix","result":[{"metric":{"xmode":"cpus"},"values":[[1773920201.070,"324"],[1773920216.070,"332"],[1773920231.070,"332"],[1773920246.070,"324"],[1773920261.070,"332"],[1773920276.070,"332"],[1773920291.070,"332"],[1773920306.070,"324"],[1773920321.070,"332"],[1773920336.070,"332"],[1773920351.070,"332"],[1773920366.070,"324"],[1773920381.070,"332"],[1773920396.070,"332"],[1773920411.070,"324"],[1773920426.070,"332"],[1773920441.070,"332"],[1773920456.070,"332"],[1773920471.070,"324"],[1773920486.070,"332"],[1773920501.070,"332"],[1773920516.070,"332"],[1773920531.070,"324"],[1773920546.070,"332"],[1773920561.070,"332"],[1773920576.070,"324"],[1773920591.070,"332"],[1773920606.070,"332"],[1773920621.070,"332"],[1773920636.070,"324"],[1773920651.070,"332"],[1773920666.070,"332"],[1773920681.070,"332"],[1773920696.070,"324"],[1773920711.070,"332"],[1773920726.070,"332"],[1773920741.070,"332"],[1773920756.070,"324"],[1773920771.070,"332"],[1773920786.070,"332"],[1773920801.070,"332"],[1773920816.070,"324"],[1773920831.070,"332"],[1773920846.070,"332"],[1773920861.070,"332"],[1773920876.070,"324"],[1773920891.070,"332"],[1773920906.070,"332"],[1773920921.070,"332"],[1773920936.070,"324"],[1773920951.070,"332"],[1773920966.070,"332"],[1773920981.070,"332"],[1773920996.070,"324"],[1773921011.070,"332"],[1773921026.070,"332"],[1773921041.070,"332"],[1773921056.070,"324"],[1773921071.070,"332"],[1773921086.070,"332"],[1773921101.070,"332"],[1773921116.070,"324"],[1773921131.070,"332"],[1773921146.070,"332"],[1773921161.070,"332"],[1773921176.070,"324"],[1773921191.070,"332"],[1773921206.070,"332"],[1773921221.070,"332"],[1773921236.070,"324"],[1773921251.070,"332"],[1773921266.070,"332"],[1773921281.070,"332"],[1773921296.070,"324"],[1773921311.070,"332"],[1773921326.070,"332"],[1773921341.070,"332"],[1773921356.070,"324"],[1773921371.070,"332"],[1773921386.070,"332"],[1773921401.070,"332"],[1773921416.070,"324"],[1773921431.070,"332"],[1773921446.070,"332"],[1773921461.070,"332"],[1773921476.070,"324"],[1773921491.070,"332"],[1773921506.070,"332"],[1773921521.070,"332"],[1773921536.070,"324"],[1773921551.070,"332"],[1773921566.070,"332"],[1773921581.070,"332"],[1773921596.070,"324"],[1773921611.070,"332"],[1773921626.070,"332"],[1773921641.070,"332"],[1773921656.070,"324"],[1773921671.070,"332"],[1773921686.070,"332"],[1773921701.070,"332"],[1773921716.070,"324"],[1773921731.070,"332"],[1773921746.070,"332"],[1773921761.070,"332"],[1773921776.070,"324"],[1773921791.070,"332"],[1773921806.070,"332"],[1773921821.070,"332"],[1773921836.070,"324"],[1773921851.070,"332"],[1773921866.070,"332"],[1773921881.070,"332"],[1773921896.070,"324"],[1773921911.070,"332"],[1773921926.070,"332"],[1773921941.070,"332"],[1773921956.070,"324"],[1773921971.070,"332"],[1773921986.070,"332"],[1773922001.070,"332"]]},{"metric":{"xmode":"load1"},"values":[[1773920201.070,"22.011699218749996"],[1773920216.070,"21.938457031250003"],[1773920231.070,"22.3469921875"],[1773920246.070,"23.176875000000003"],[1773920261.070,"25.36408203125"],[1773920276.070,"26.613906249999996"],[1773920291.070,"27.084453125"],[1773920306.070,"27.131035156249997"],[1773920321.070,"25.17642578125"],[1773920336.070,"26.73248046875"],[1773920351.070,"29.7228515625"],[1773920366.070,"27.339140625"],[1773920381.070,"23.79078125"],[1773920396.070,"23.530410156249996"],[1773920411.070,"23.573242187499996"],[1773920426.070,"23.80716796875"],[1773920441.070,"25.33857421875"],[1773920456.070,"24.367578124999998"],[1773920471.070,"24.165546874999997"],[1773920486.070,"25.815390625"],[1773920501.070,"26.4998046875"],[1773920516.070,"25.91193359375"],[1773920531.070,"26.06251953125"],[1773920546.070,"27.363945312500004"],[1773920561.070,"26.41671875"],[1773920576.070,"25.856562500000003"],[1773920591.070,"27.439648437500004"],[1773920606.070,"27.71337890625"],[1773920621.070,"27.076210937500004"],[1773920636.070,"30.5535546875"],[1773920651.070,"28.36044921875"],[1773920666.070,"26.616914062499998"],[1773920681.070,"27.221953125000002"],[1773920696.070,"27.954550781249996"],[1773920711.070,"25.43552734375"],[1773920726.070,"28.469082031250004"],[1773920741.070,"25.578320312500004"],[1773920756.070,"27.50509765625"],[1773920771.070,"27.1834375"],[1773920786.070,"27.318828124999992"],[1773920801.070,"28.550078125000006"],[1773920816.070,"30.152265625"],[1773920831.070,"27.838281250000005"],[1773920846.070,"28.1693359375"],[1773920861.070,"26.795390624999996"],[1773920876.070,"24.695507812499997"],[1773920891.070,"25.26046875"],[1773920906.070,"23.354550781249998"],[1773920921.070,"24.897421875"],[1773920936.070,"25.83609375"],[1773920951.070,"26.6660546875"],[1773920966.070,"25.19197265625"],[1773920981.070,"28.366972656250002"],[1773920996.070,"28.579042968750002"],[1773921011.070,"29.791210937499997"],[1773921026.070,"30.95927734375"],[1773921041.070,"32.099589843749996"],[1773921056.070,"31.46384765625"],[1773921071.070,"32.12513671875"],[1773921086.070,"31.46900390625"],[1773921101.070,"29.264824218750007"],[1773921116.070,"26.411074218750002"],[1773921131.070,"27.8246875"],[1773921146.070,"27.951777343750003"],[1773921161.070,"31.54697265625"],[1773921176.070,"29.656015624999995"],[1773921191.070,"27.518261718749997"],[1773921206.070,"26.37123046875"],[1773921221.070,"24.155566406250003"],[1773921236.070,"22.395117187500002"],[1773921251.070,"23.91759765625"],[1773921266.070,"26.395390625"],[1773921281.070,"23.423046875"],[1773921296.070,"22.8750390625"],[1773921311.070,"22.140546875"],[1773921326.070,"25.68294921875"],[1773921341.070,"26.139277343750003"],[1773921356.070,"29.508867187499998"],[1773921371.070,"30.3898046875"],[1773921386.070,"28.43595703125"],[1773921401.070,"29.087851562500003"],[1773921416.070,"34.40611328125001"],[1773921431.070,"34.903066406250005"],[1773921446.070,"32.50927734375"],[1773921461.070,"33.6397265625"],[1773921476.070,"34.23193359375"],[1773921491.070,"33.768652343750006"],[1773921506.070,"34.113300781250004"],[1773921521.070,"30.05490234375"],[1773921536.070,"30.04658203125"],[1773921551.070,"28.757695312499997"],[1773921566.070,"33.600566406249996"],[1773921581.070,"32.860625"],[1773921596.070,"32.142812500000005"],[1773921611.070,"34.39669921875"],[1773921626.070,"40.62568359375"],[1773921641.070,"40.3808984375"],[1773921656.070,"46.3801171875"],[1773921671.070,"42.78142578125"],[1773921686.070,"40.63814453125"],[1773921701.070,"40.34443359375"],[1773921716.070,"36.846660156249996"],[1773921731.070,"33.94181640625"],[1773921746.070,"34.15931640625"],[1773921761.070,"37.370000000000005"],[1773921776.070,"36.536367187500005"],[1773921791.070,"33.0490234375"],[1773921806.070,"32.661503906250005"],[1773921821.070,"33.658671874999996"],[1773921836.070,"35.196269531249996"],[1773921851.070,"36.534765625000006"],[1773921866.070,"36.90380859375001"],[1773921881.070,"35.128046875"],[1773921896.070,"33.588339843750006"],[1773921911.070,"33.64333984375"],[1773921926.070,"34.875585937500006"],[1773921941.070,"33.00701171874999"],[1773921956.070,"31.039355468750003"],[1773921971.070,"30.184765624999997"],[1773921986.070,"32.651328125"],[1773922001.070,"30.539160156250002"]]},{"metric":{"xmode":"nodes"},"values":[[1773920201.070,"34"],[1773920216.070,"35"],[1773920231.070,"35"],[1773920246.070,"34"],[1773920261.070,"35"],[1773920276.070,"35"],[1773920291.070,"35"],[1773920306.070,"34"],[1773920321.070,"35"],[1773920336.070,"35"],[1773920351.070,"35"],[1773920366.070,"34"],[1773920381.070,"35"],[1773920396.070,"35"],[1773920411.070,"34"],[1773920426.070,"35"],[1773920441.070,"35"],[1773920456.070,"35"],[1773920471.070,"34"],[1773920486.070,"35"],[1773920501.070,"35"],[1773920516.070,"35"],[1773920531.070,"34"],[1773920546.070,"35"],[1773920561.070,"35"],[1773920576.070,"34"],[1773920591.070,"35"],[1773920606.070,"35"],[1773920621.070,"35"],[1773920636.070,"34"],[1773920651.070,"35"],[1773920666.070,"35"],[1773920681.070,"35"],[1773920696.070,"34"],[1773920711.070,"35"],[1773920726.070,"35"],[1773920741.070,"35"],[1773920756.070,"34"],[1773920771.070,"35"],[1773920786.070,"35"],[1773920801.070,"35"],[1773920816.070,"34"]]}]}}"""

    @Test
    fun testValidSampleInput() {
        Json.decodeFromString<JsonObject>(sampleInput)
    }

    @Test
    fun testPushParserOnePass() {
        val p = JsonParser()
        p.push(sampleInput.encodeToByteArray())
        p.finish()
        while (true) {
            val next = p.next() ?: break
            println(next)
        }
    }

    @Test
    fun testPushParserTwoPass() {
        val data = sampleInput.encodeToByteArray()
        val p = JsonParser()

        p.push(data.copyOfRange(0, 43))
        while (true) {
            val next = p.next()
            require(next != null)
            if (next is JsonEvent.InProgress) {
                break
            }
            println(next)
        }
        p.push(data.copyOfRange(44, data.size))
        p.finish()
        while (true) {
            val next = p.next() ?: break
            require(next !is JsonEvent.InProgress)
            println(next)
        }
    }

    @Test
    fun testParseError() {
        val data = sampleInput.encodeToByteArray()
        val p = JsonParser()

        p.push(data.copyOfRange(0, 43))
        p.finish()
        assertFails {
            while (true) {
                val next = p.next() ?: break
                require(next !is JsonEvent.InProgress)
                println(next)
            }
        }
    }

    private fun parseAll(input: String): List<Pair<JsonEvent, String?>> {
        val p = JsonParser()
        p.push(input.encodeToByteArray())
        p.finish()
        val events = mutableListOf<Pair<JsonEvent, String?>>()
        while (true) {
            val next = p.next() ?: break
            require(next !is JsonEvent.InProgress)
            events.add(next to p.currentValue())
        }
        return events
    }

    @Test
    fun testWhitespaceAroundStructuralTokens() {
        val input = """ { "key" : "value" } """
        val events = parseAll(input)
        assertEquals(JsonEvent.StartObject, events[0].first)
        assertEquals(JsonEvent.Value, events[1].first)
        assertEquals("\"key\"", events[1].second)
        assertEquals(JsonEvent.Value, events[2].first)
        assertEquals("\"value\"", events[2].second)
        assertEquals(JsonEvent.EndObject, events[3].first)
        assertEquals(4, events.size)
    }

    @Test
    fun testWhitespaceInArray() {
        val input = """[ 1 , 2 , 3 ]"""
        val events = parseAll(input)
        assertEquals(JsonEvent.StartArray, events[0].first)
        assertEquals(JsonEvent.Value, events[1].first)
        assertEquals("1", events[1].second)
        assertEquals(JsonEvent.Value, events[2].first)
        assertEquals("2", events[2].second)
        assertEquals(JsonEvent.Value, events[3].first)
        assertEquals("3", events[3].second)
        assertEquals(JsonEvent.EndArray, events[4].first)
        assertEquals(5, events.size)
    }

    @Test
    fun testNewlinesAndTabs() {
        val input = "{\n\t\"a\"\t:\t\"b\"\n}"
        val events = parseAll(input)
        assertEquals(JsonEvent.StartObject, events[0].first)
        assertEquals(JsonEvent.Value, events[1].first)
        assertEquals("\"a\"", events[1].second)
        assertEquals(JsonEvent.Value, events[2].first)
        assertEquals("\"b\"", events[2].second)
        assertEquals(JsonEvent.EndObject, events[3].first)
    }

    @Test
    fun testLeadingAndTrailingWhitespace() {
        val input = "  \n\t [1,2]  \n "
        val events = parseAll(input)
        assertEquals(JsonEvent.StartArray, events[0].first)
        assertEquals(JsonEvent.Value, events[1].first)
        assertEquals("1", events[1].second)
        assertEquals(JsonEvent.Value, events[2].first)
        assertEquals("2", events[2].second)
        assertEquals(JsonEvent.EndArray, events[3].first)
        assertEquals(4, events.size)
    }

    @Test
    fun testWhitespaceAroundPlainValues() {
        val input = """{ "a" : true , "b" : null , "c" : false }"""
        val events = parseAll(input)
        assertEquals(JsonEvent.StartObject, events[0].first)
        assertEquals("\"a\"", events[1].second)
        assertEquals("true", events[2].second)
        assertEquals("\"b\"", events[3].second)
        assertEquals("null", events[4].second)
        assertEquals("\"c\"", events[5].second)
        assertEquals("false", events[6].second)
        assertEquals(JsonEvent.EndObject, events[7].first)
    }

    @Test
    fun testEmptyObjectWithWhitespace() {
        val events = parseAll(" {  } ")
        assertEquals(JsonEvent.StartObject, events[0].first)
        assertEquals(JsonEvent.EndObject, events[1].first)
        assertEquals(2, events.size)
    }

    @Test
    fun testEmptyArrayWithWhitespace() {
        val events = parseAll(" [  ] ")
        assertEquals(JsonEvent.StartArray, events[0].first)
        assertEquals(JsonEvent.EndArray, events[1].first)
        assertEquals(2, events.size)
    }

    @Test
    fun testNestedWithWhitespace() {
        val input = """
        {
            "arr" : [ 1 , { "nested" : true } ]
        }
        """
        val events = parseAll(input)
        assertEquals(JsonEvent.StartObject, events[0].first)
        assertEquals("\"arr\"", events[1].second)
        assertEquals(JsonEvent.StartArray, events[2].first)
        assertEquals("1", events[3].second)
        assertEquals(JsonEvent.StartObject, events[4].first)
        assertEquals("\"nested\"", events[5].second)
        assertEquals("true", events[6].second)
        assertEquals(JsonEvent.EndObject, events[7].first)
        assertEquals(JsonEvent.EndArray, events[8].first)
        assertEquals(JsonEvent.EndObject, events[9].first)
        assertEquals(10, events.size)
    }

    @Test
    fun testWhitespaceWithIncrementalPush() {
        val input = " { \"key\" : 42 } "
        val data = input.encodeToByteArray()
        val p = JsonParser()

        // Push just the leading whitespace and opening brace
        p.push(data.copyOfRange(0, 3))
        val events = mutableListOf<Pair<JsonEvent, String?>>()
        while (true) {
            val next = p.next()
            require(next != null)
            if (next is JsonEvent.InProgress) break
            events.add(next to p.currentValue())
        }
        assertEquals(1, events.size)
        assertEquals(JsonEvent.StartObject, events[0].first)

        // Push the rest
        p.push(data.copyOfRange(3, data.size))
        p.finish()
        while (true) {
            val next = p.next() ?: break
            require(next !is JsonEvent.InProgress)
            events.add(next to p.currentValue())
        }
        assertEquals(4, events.size) // StartObject + key + value + EndObject
    }
}
