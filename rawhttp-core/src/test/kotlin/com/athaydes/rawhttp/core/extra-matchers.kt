package com.athaydes.rawhttp.core

import io.kotlintest.matchers.Matcher
import io.kotlintest.matchers.Result
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
