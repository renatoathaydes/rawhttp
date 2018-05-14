package rawhttp.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import rawhttp.core.errors.InvalidHttpHeader;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toMap;

/**
 * A Collection of HTTP headers.
 * <p>
 * Header names are case-insensitive, as per <a href="https://tools.ietf.org/html/rfc7230#section-3">Section 3</a>
 * of RFC-7230, so lookup methods return header values regardless of the case of the name.
 * <p>
 * The headers are also kept in the order that they were added, except in cases where the same header is added multiple
 * times, in which case the new values are grouped together with the previous ones.
 *
 * @see HttpMessage
 */
public class RawHttpHeaders {

    private final Map<String, Header> headersByCapitalizedName;
    private final List<String> headerNames;

    private static final Header NULL_HEADER = new Header(emptyList());

    private RawHttpHeaders(Map<String, Header> headersByCapitalizedName,
                           List<String> headerNames,
                           boolean isModifiable) {
        if (isModifiable) {
            Map<String, Header> headers = new LinkedHashMap<>(headersByCapitalizedName);
            headers.entrySet().forEach(entry -> entry.setValue(entry.getValue().freeze()));
            this.headersByCapitalizedName = unmodifiableMap(headers);
            this.headerNames = unmodifiableList(new ArrayList<>(headerNames));
        } else {
            this.headersByCapitalizedName = headersByCapitalizedName;
            this.headerNames = headerNames;
        }
    }

    /**
     * @param headerName case-insensitive header name
     * @return values for the header, or the empty list if this header is not present.
     */
    public List<String> get(String headerName) {
        return headersByCapitalizedName.getOrDefault(headerName.toUpperCase(), NULL_HEADER).values;
    }

    /**
     * @param headerName case-insensitive header name
     * @return the first value of the header, if any.
     */
    public Optional<String> getFirst(String headerName) {
        List<String> values = get(headerName);
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(values.get(0));
        }
    }

    /**
     * @return the names of all headers (in the case and order that they were inserted). As headers may appear more
     * than once, this method may return duplicates.
     * @see #getUniqueHeaderNames()
     */
    public List<String> getHeaderNames() {
        return headerNames;
    }

    /**
     * @return the unique names of all headers (names are upper-cased).
     */
    public Set<String> getUniqueHeaderNames() {
        return headersByCapitalizedName.keySet();
    }

    /**
     * Check if the given header name is present in this set of headers.
     *
     * @param headerName case-insensitive header name
     * @return true if the header is present, false otherwise.
     */
    public boolean contains(String headerName) {
        return getUniqueHeaderNames().contains(toUppercaseAscii(headerName));
    }

    /**
     * @return a {@link Map} representation of this set of headers.
     */
    public Map<String, List<String>> asMap() {
        return headersByCapitalizedName.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawHttpHeaders that = (RawHttpHeaders) o;

        boolean sameKeys = headersByCapitalizedName.keySet().equals(that.headersByCapitalizedName.keySet());

        if (!sameKeys) {
            return false;
        }

        // check all values
        for (Map.Entry<String, Header> entry : headersByCapitalizedName.entrySet()) {
            if (!that.headersByCapitalizedName.get(entry.getKey()).values.equals(entry.getValue().values)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Iterate over all entries in this set of headers.
     * <p>
     * The consumer is called for each value of a header. If a header has multiple values, each value is consumed
     * once, so the same header name (with the original case it was inserted with) may be consumed more than once.
     *
     * @param consumer accepts the header name and value
     */
    public void forEach(BiConsumer<String, String> consumer) {
        class Index {
            private int value = -1;

            int increment() {
                return ++value;
            }
        }
        Map<String, Index> valueIndexByKey = new HashMap<>();
        for (String headerName : headerNames) {
            String key = toUppercaseAscii(headerName);
            int index = valueIndexByKey.computeIfAbsent(key, k -> new Index()).increment();
            consumer.accept(headerName, headersByCapitalizedName.get(key).values.get(index));
        }
    }

    void forEachIO(IOBiConsumer<String, String> consumer) throws IOException {
        try {
            forEach((name, value) -> {
                try {
                    consumer.accept(name, value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public int hashCode() {
        return headersByCapitalizedName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        forEach((name, value) ->
                builder.append(name).append(": ").append(value).append("\r\n"));
        return builder.append("\r\n").toString();
    }

    /**
     * Create a new set of headers, adding/replacing the provided headers into this instance.
     * <p>
     * Multi-valued headers present in both this and the provided headers are not merged. The provided headers
     * are guaranteed to be present and have the same values in the returned instance.
     *
     * @param headers to add or replace on this.
     * @return new set of headers containing both this instance's values as well as the provided values
     */
    public RawHttpHeaders and(RawHttpHeaders headers) {
        Builder builder = RawHttpHeaders.newBuilderSkippingValidation(this);
        Set<String> visitedNames = new HashSet<>(headers.headerNames.size());
        headers.forEach((name, value) -> {
            String key = toUppercaseAscii(name);
            boolean isNewKey = visitedNames.add(key);
            if (isNewKey) {
                builder.overwrite(name, value);
            } else {
                builder.with(name, value);
            }
        });
        return builder.build();
    }

    /**
     * Create a new builder containing all values of the given headers.
     *
     * @param headers to start from
     * @return new builder
     */
    public static Builder newBuilder(RawHttpHeaders headers) {
        return Builder.newBuilder(headers, true);
    }

    /**
     * Create a new builder that does not validate header fields,
     * containing all values of the given headers.
     *
     * @param headers to start from
     * @return new builder
     */
    public static Builder newBuilderSkippingValidation(RawHttpHeaders headers) {
        return Builder.newBuilder(headers, false);
    }

    /**
     * Create a new, empty builder.
     *
     * @return new builder
     */
    public static Builder newBuilder() {
        return new Builder(true);
    }

    /**
     * Create a new, empty builder that does not validate header fields.
     *
     * @return new builder
     */
    public static Builder newBuilderSkippingValidation() {
        return new Builder(false);
    }

    public static RawHttpHeaders empty() {
        return Builder.EMPTY;
    }

    private static String toUppercaseAscii(String s) {
        char[] source = s.toCharArray();
        char[] result = new char[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = toUppercaseAscii(source[i]);
        }
        return new String(result);
    }

    private static char toUppercaseAscii(char c) {
        return ('a' <= c && c <= 'z') ? (char) (c - 32) : c;
    }

    /**
     * Builder of {@link RawHttpHeaders}.
     */
    public static final class Builder {

        private static final RawHttpHeaders EMPTY = new RawHttpHeaders(emptyMap(), emptyList(), false);

        private final boolean validateHeaders;
        private final LinkedHashMap<String, Header> headersByCapitalizedName = new LinkedHashMap<>();
        private final List<String> headerNames = new ArrayList<>();

        private Builder(boolean validateHeaders) {
            this.validateHeaders = validateHeaders;
        }

        private static Builder newBuilder(RawHttpHeaders headers, boolean validateHeaders) {
            Builder builder = new Builder(validateHeaders);
            for (Map.Entry<String, Header> entry : headers.headersByCapitalizedName.entrySet()) {
                builder.headersByCapitalizedName.put(entry.getKey(), entry.getValue().unfreeze());
            }
            builder.headerNames.addAll(headers.getHeaderNames());
            return builder;
        }

        /**
         * Create a new, empty, immutable instance of {@link RawHttpHeaders}.
         *
         * @return empty headers
         */
        public static RawHttpHeaders emptyRawHttpHeaders() {
            return EMPTY;
        }

        /**
         * @param headerName case-insensitive header name
         * @return values for the header, or the empty list if this header is not present.
         */
        public List<String> get(String headerName) {
            return headersByCapitalizedName.getOrDefault(
                    toUppercaseAscii(headerName), NULL_HEADER).values;
        }

        /**
         * Include the given header in this builder.
         * <p>
         * This method takes a line number so that a HTTP message parser may query in which lines a
         * certain header appeared later.
         *
         * @param headerName header name
         * @param value      header value
         * @return this
         */
        public Builder with(String headerName, String value) {
            char[] upperCaseHeaderNameChars = new char[headerName.length()];
            char[] headerNameChars = headerName.toCharArray();
            for (int i = 0; i < headerNameChars.length; i++) {
                char c = headerNameChars[i];
                if (validateHeaders && !FieldValues.isAllowedInTokens(c)) {
                    throw new InvalidHttpHeader("Invalid header name (contains illegal character at index " +
                            i + "): " + headerName);
                }
                upperCaseHeaderNameChars[i] = toUppercaseAscii(c);
            }
            final String upperCaseHeaderName = new String(upperCaseHeaderNameChars);
            if (validateHeaders) {
                OptionalInt illegalIndex = FieldValues.indexOfNotAllowedInVCHARs(value);
                if (illegalIndex.isPresent()) {
                    throw new InvalidHttpHeader("Invalid header value (contains illegal character at index " +
                            illegalIndex.getAsInt() + "): " + value);
                }
            }
            headersByCapitalizedName.computeIfAbsent(upperCaseHeaderName,
                    (ignore) -> new Header(new ArrayList<>(2))).values.add(value);
            headerNames.add(headerName);
            return this;
        }

        /**
         * Overwrite the given header with the single value provided.
         *
         * @param headerName header name
         * @param value      single value for the header
         * @return this
         */
        public Builder overwrite(String headerName, String value) {
            if (validateHeaders) {
                OptionalInt illegalNameChar = FieldValues.indexOfNotAllowedInTokens(headerName);
                if (illegalNameChar.isPresent()) {
                    throw new InvalidHttpHeader("Invalid header name (contains illegal character at index " +
                            illegalNameChar.getAsInt() + "): " + headerName);
                }
                OptionalInt illegalValueChar = FieldValues.indexOfNotAllowedInVCHARs(value);
                if (illegalValueChar.isPresent()) {
                    throw new InvalidHttpHeader("Invalid header value (contains illegal character at index " +
                            illegalValueChar.getAsInt() + "): " + value);
                }
            }
            String key = toUppercaseAscii(headerName);
            headersByCapitalizedName.put(key, new Header(value));
            AtomicBoolean foundFirst = new AtomicBoolean(false);
            headerNames.removeIf(name -> {
                boolean found = name.equalsIgnoreCase(headerName);
                if (found && foundFirst.weakCompareAndSet(false, true)) {
                    return false; // leave the first value
                }
                return found;
            });
            if (!foundFirst.get()) {
                // header did not exist yet, add it
                headerNames.add(headerName);
            }
            return this;
        }

        /**
         * Remove the header with the given name (including all values).
         *
         * @param headerName case-insensitive header name
         */
        public void remove(String headerName) {
            String key = toUppercaseAscii(headerName);
            headersByCapitalizedName.remove(key);
            headerNames.removeIf(name -> name.equalsIgnoreCase(headerName));
        }

        /**
         * Merge this builder's headers with the ones provided.
         *
         * @param headers to merge with this builder
         * @return this
         */
        public Builder merge(RawHttpHeaders headers) {
            headers.forEach(this::with);
            return this;
        }

        /**
         * @return new instance of {@link RawHttpHeaders} with all headers added to this builder.
         */
        public RawHttpHeaders build() {
            return new RawHttpHeaders(headersByCapitalizedName, headerNames, true);
        }

        /**
         * @return the names of all headers added to this builder.
         */
        public List<String> getHeaderNames() {
            return unmodifiableList(headerNames);
        }

        /**
         * @param headerName name of the header (case-insensitive)
         * @param index      index of the header to find (for example, to find the first appearance, use 0,
         *                   the second appearance, 1, and so on)
         * @return the line number where the header with the given name appeared at the given index,
         * or -1 if the header is not present at the given index
         */
        int getLineNumberAt(String headerName, int index) {
            int currentIndex = 0;
            int lineNumber = 2;
            for (String name : headerNames) {
                if (name.equalsIgnoreCase(headerName)) {
                    if (currentIndex == index) {
                        return lineNumber;
                    }
                    currentIndex++;
                }
                lineNumber++;
            }
            throw new NoSuchElementException();
        }

    }

    private static final class Header {
        private final List<String> values;

        Header(String value) {
            this(new ArrayList<>(singletonList(value)));
        }

        Header(List<String> values) {
            this.values = values;
        }

        Header freeze() {
            return new Header(unmodifiableList(values));
        }

        Header unfreeze() {
            return new Header(new ArrayList<>(values));
        }
    }

}
