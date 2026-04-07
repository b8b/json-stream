import kotlinx.serialization.json.*
import org.cikit.jsonstream.JsonEvent
import org.cikit.jsonstream.JsonParser
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSuite {

    val basePath = Path("JSONTestSuite/test_parsing")

    @Test
    fun `test y_number_double_close_to_zero`() {
        assertEquals(null, testParse(basePath / "y_number_double_close_to_zero.json", st = true))
    }

    @Test
    fun runTestSuite() {
        var testCount = 0
        var passedCount = 0
        var neutralOkCount = 0
        var neutralFailCount = 0
        val outFile = "result.txt"
        Path(outFile).bufferedWriter().use { w ->
            for (p in basePath.listDirectoryEntries()) {
                testCount++
                val name = p.name
                val (expect, actual) = when {
                    name.startsWith("i") -> null to testParse(p)
                    name.startsWith("n") -> false to testParse(p)
                    name.startsWith("y") -> true to testParse(p)

                    else -> continue
                }
                if (expect == null) {
                    if (actual != null) {
                        w.appendLine("$name: $actual")
                        neutralFailCount++
                    } else {
                        neutralOkCount++
                    }
                } else if (expect) {
                    if (actual != null) {
                        w.appendLine("$name: $actual")
                    } else {
                        passedCount++
                    }
                } else {
                    if (actual == null) {
                        w.appendLine("$name: $actual")
                    } else {
                        passedCount++
                    }
                }
            }
        }
        val totalMust = testCount - neutralOkCount - neutralFailCount
        println("passed $passedCount / $totalMust tests")
        println("passed $neutralOkCount neutral tests")
        println("failed $neutralFailCount neutral tests")
    }


    private fun testParse(
        p: Path,
        st: Boolean = false,
        trim: Boolean = false
    ): String? {
        try {
            val parser = JsonParser()
            val bytes = if (trim) {
                p.readText().trim().encodeToByteArray()
            } else {
                p.readBytes()
            }
            parser.push(bytes)
            parser.finish()
            var i = 0
            while (true) {
                if (i++ > 100_000) {
                    error("i = $i")
                }
                val ev = parser.next() ?: break
                if (ev is JsonEvent.Value) {
                    val verifyPrimitive = parser.currentValue()
                        ?.let { Json.decodeFromString<JsonElement>(it).jsonPrimitive }
                        ?: JsonNull
                    if (!verifyPrimitive.isString && verifyPrimitive !is JsonNull) {
                        verifyPrimitive.booleanOrNull
                            ?: verifyPrimitive.intOrNull
                            ?: verifyPrimitive.longOrNull
                            ?: verifyPrimitive.floatOrNull
                            ?: verifyPrimitive.doubleOrNull
                            ?: error("unrecognized primitive: $verifyPrimitive")
                    }
                }
            }
            return null
        } catch (ex: Exception) {
            return buildString {
                append(Json.encodeToString(ex.toString()))
                if (st) {
                    appendLine()
                    for (s in ex.stackTraceToString().trim().split("\n")) {
                        appendLine(Json.encodeToString(s))
                    }
                }
            }
        }
    }
}