package rawhttp.core

import io.kotlintest.matchers.Matcher
import io.kotlintest.matchers.Result
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.beLessThanOrEqualTo
import io.kotlintest.matchers.should
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Optional

class OptionalMatcher<T>(private val expectPresent: Boolean,
                         private val valueMatcher: ((T) -> Any?)? = null) : Matcher<Optional<T>> {
    override fun test(value: Optional<T>) = when (value.isPresent) {
        true ->
            if (expectPresent) if (valueMatcher != null) {
                valueMatcher.invoke(value.get())
                Result(true, "")
            } else Result(true, "")
            else Result(false, "Expected value not present but got '${value.get()}'")
        false ->
            if (!expectPresent) Result(true, "")
            else Result(false, "Expected value present but was empty")
    }
}

fun <T> bePresent(valueMatcher: ((T) -> Any?)? = null) = OptionalMatcher(true, valueMatcher)
fun <T> notBePresent() = OptionalMatcher<T>(false)

infix fun <T> T.shouldBeOneOf(options: Set<T>) {
    if (options.isEmpty()) {
        throw IllegalStateException("Assertion will never succeed as no options were provided")
    }
    if (this !in options) {
        throw AssertionError("Value '$this' is not one of:\n${options.map { "  * '$it'" }.joinToString("\n")}")
    }
}

infix fun ByteArray.shouldHaveSameElementsAs(other: ByteArray) {
    val expected = other.toList()
    val actual = this.toList()
    if (expected.size != actual.size) {
        throw AssertionError("expected array of size ${expected.size} but got array of size ${actual.size}")
    }
    for ((index, values) in expected.zip(actual).withIndex()) {
        val (e, a) = values
        if (e != a) {
            throw AssertionError("expected $e but got $a at index $index")
        }
    }
}

data class HttHeadersMatcher(private val tolerance: Duration) : Matcher<RawHttpHeaders> {

    override fun test(value: RawHttpHeaders): Result {
        val dateHeaderValues = value["Date"]
                ?: return Result(false, "Date header is missing from: $value")
        val dateHeaderValue = dateHeaderValues.first()
        val parsedDate = try {
            LocalDateTime.parse(dateHeaderValue, DateTimeFormatter.RFC_1123_DATE_TIME)
        } catch (e: DateTimeParseException) {
            return Result(false, "Date header value is invalid ($dateHeaderValue): $e")
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)

        parsedDate should beLessThanOrEqualTo(now)
        parsedDate should beGreaterThan(now.minus(tolerance))

        return Result(true, "")
    }

}

fun validDateHeader(tolerance: Duration = Duration.ofSeconds(5)) = HttHeadersMatcher(tolerance)
