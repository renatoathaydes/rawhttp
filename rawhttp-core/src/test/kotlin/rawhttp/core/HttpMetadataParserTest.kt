package rawhttp.core

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import org.junit.Test

class HttpMetadataParserTest {

    private val parser = HttpMetadataParser(RawHttpOptions.defaultInstance())
    private val errorCreator = { error: String, line: Int -> throw Exception("$error ($line)") }

    @Test
    fun canParseSimpleHeaders() {
        val examples = table(
                headers("HTTP Header", "Name", "Value"),
                row("Accept: application/json", "Accept", "application/json"),
                row("Accept: text/xml;q=1.0,app/json;q=0.8,image/png+x", "Accept", "text/xml;q=1.0,app/json;q=0.8,image/png+x"),
                row("Content-Length:  42", "Content-Length", "42"),
                row("Content-Length:42   ", "Content-Length", "42")
        )

        forAll(examples) { header, expectedName, expectedValue ->
            val headers = parser.parseHeaders(header.byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers[expectedName] shouldEqual listOf(expectedValue)
        }
    }

}