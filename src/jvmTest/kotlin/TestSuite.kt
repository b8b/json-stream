import org.cikit.jsonstream.JsonEvent
import org.cikit.jsonstream.JsonParseException
import org.cikit.jsonstream.JsonParser
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestSuite {

    private val basePath: Path = Path(
        System.getProperty("jsonTestSuite.dir") ?: "JSONTestSuite/test_parsing"
    ).absolute()

    private val reportPath: Path = Path(
        System.getProperty("jsonTestSuite.report")
            ?: "build/reports/jsonTestSuite.txt"
    ).absolute()

    @Test
    fun testConformance() {
        if (!basePath.exists()) {
            fail(
                "JSONTestSuite not found at $basePath, " +
                    "run ./gradlew fetchJsonTestSuite"
            )
        }
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        var required = 0
        var implementationDefined = 0
        for (p in basePath.listDirectoryEntries().sortedBy { it.name }) {
            val name = p.name
            val kind = name.firstOrNull()?.toString()
            if (kind != "y" && kind != "n" && kind != "i") {
                continue
            }
            val error = parseFully(p.readBytes())
            val problem = when (kind) {
                "y" -> if (error == null) {
                    null
                } else {
                    "valid document rejected: ${describe(error)}"
                }
                "n" -> when {
                    error == null -> "invalid document accepted"
                    error !is JsonParseException ->
                        "wrong exception type: ${describe(error)}"
                    else -> null
                }
                else -> null
            }
            if (kind == "i") {
                implementationDefined++
            } else {
                required++
            }
            report.appendLine("$name: ${describe(error)}")
            if (problem != null) {
                failures.add("$name: $problem")
            }
        }
        if (required == 0) {
            fail("no JSONTestSuite cases found in $basePath")
        }
        reportPath.parent.createDirectories()
        reportPath.writeText(
            "failed ${failures.size} of $required required cases, " +
                "$implementationDefined implementation-defined cases\n\n" +
                report
        )
        assertEquals(emptyList(), failures)
    }

    private fun describe(error: Exception?): String {
        return if (error == null) {
            "ok"
        } else {
            "${error::class.simpleName}: ${error.message}"
        }
    }

    private fun parseFully(bytes: ByteArray): Exception? {
        val parser = JsonParser()
        return try {
            parser.push(bytes)
            parser.finish()
            while (true) {
                val event = parser.next() ?: break
                if (event is JsonEvent.Value) {
                    parser.currentValue()
                }
            }
            null
        } catch (e: Exception) {
            e
        }
    }
}
