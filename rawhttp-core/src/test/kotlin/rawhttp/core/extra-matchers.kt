package rawhttp.core

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

infix fun ByteArray.shouldHaveSameElementsAs(other: ByteArray) {
    val expected = other.toList()
    val actual = this.toList()
    if (expected.size != actual.size) {
        throw AssertionError(
            "expected array of size ${expected.size} but got array of size ${actual.size}\n" +
                    "Actual:   ${actual.toShortString()}\n" +
                    "Expected: ${expected.toShortString()}"
        )
    }
    for ((index, values) in expected.zip(actual).withIndex()) {
        val (e, a) = values
        if (e != a) {
            throw AssertionError("expected $e but got $a at index $index")
        }
    }
}

private fun List<*>.toShortString(): String {
    return if (size > 100) {
        "[${this.subList(0, 100).joinToString()} ...]"
    } else {
        toString()
    }
}

data class HttHeadersMatcher(private val tolerance: Duration) : Matcher<RawHttpHeaders> {

    override fun test(value: RawHttpHeaders): MatcherResult {
        val dateHeaderValues = value["Date"]
            ?: return MatcherResult(false,
                { "Date header is missing from: $value" },
                { "Date header is not missing from: $value" })
        val dateHeaderValue = dateHeaderValues.first()
        val parsedDate = try {
            LocalDateTime.parse(dateHeaderValue, DateTimeFormatter.RFC_1123_DATE_TIME)
        } catch (e: DateTimeParseException) {
            return MatcherResult(false,
                { "Date header value is invalid ($dateHeaderValue): $e" },
                { "Date header value is valid ($dateHeaderValue): $e" })
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)

        parsedDate shouldBeLessThanOrEqualTo (now)
        parsedDate shouldBeGreaterThan (now.minus(tolerance))

        return MatcherResult(true, { "" }, { "" })
    }

}

fun validDateHeader(tolerance: Duration = Duration.ofSeconds(5)) = HttHeadersMatcher(tolerance)
