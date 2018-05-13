package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.errors.InvalidHttpHeader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
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

    private static final Header NULL_HEADER = new Header("").freeze();

    private RawHttpHeaders(Map<String, Header> headersByCapitalizedName, boolean isModifiableMap) {
        if (isModifiableMap) {
            Map<String, Header> headers = new LinkedHashMap<>(headersByCapitalizedName);
            headers.entrySet().forEach(entry -> entry.setValue(entry.getValue().freeze()));
            this.headersByCapitalizedName = unmodifiableMap(headers);
        } else {
            this.headersByCapitalizedName = headersByCapitalizedName;
        }
    }

    private RawHttpHeaders(RawHttpHeaders first, RawHttpHeaders second) {
        this(concatenate(first.headersByCapitalizedName, second.headersByCapitalizedName), false);
    }

    private static Map<String, Header> concatenate(Map<String, Header> first, Map<String, Header> second) {
        Map<String, Header> headers = new LinkedHashMap<>(first.size() + second.size());
        headers.putAll(first);
        headers.putAll(second);
        return unmodifiableMap(headers);
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
        List<String> result = new ArrayList<>(headersByCapitalizedName.size());
        forEach((name, v) -> result.add(name));
        return result;
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
        return getUniqueHeaderNames().contains(headerName.toUpperCase());
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
     * once, so the header name may be consumed more than once.
     *
     * @param consumer accepts the header name and value
     */
    public void forEach(BiConsumer<String, String> consumer) {
        headersByCapitalizedName.forEach((k, v) ->
                v.values.forEach(value ->
                        consumer.accept(v.originalHeaderName, value)));
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
        return new RawHttpHeaders(this, headers);
    }

    /**
     * Create a new builder containing all values of the given headers.
     *
     * @param headers to start from
     * @return new builder
     */
    public static Builder newBuilder(RawHttpHeaders headers) {
        return Builder.newBuilder(headers);
    }

    /**
     * Create a new, empty builder.
     *
     * @return new builder
     */
    public static Builder newBuilder() {
        return Builder.newBuilder();
    }

    public static RawHttpHeaders empty() {
        return Builder.EMPTY;
    }

    /**
     * Builder of {@link RawHttpHeaders}.
     */
    public static class Builder {

        private static final RawHttpHeaders EMPTY =
                new RawHttpHeaders(emptyMap(), false);

        private final Map<String, List<Integer>> linesPerHeader = new HashMap<>(8);

        /**
         * Create a new builder containing all values of the given headers.
         *
         * @param headers to start from
         * @return new builder
         */
        public static Builder newBuilder(RawHttpHeaders headers) {
            Builder builder = new Builder();
            headers.headersByCapitalizedName.forEach((k, v) ->
                    builder.headersByCapitalizedName.put(k, v.unfreeze()));
            return builder;
        }

        /**
         * Create a new, empty builder.
         *
         * @return new builder
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Create a new, empty, immutable instance of {@link RawHttpHeaders}.
         *
         * @return empty headers
         */
        public static RawHttpHeaders emptyRawHttpHeaders() {
            return new RawHttpHeaders(emptyMap(), false);
        }

        private Builder() {
            // hide
        }

        private final Map<String, Header> headersByCapitalizedName = new LinkedHashMap<>();

        /**
         * Include the given header in this builder.
         *
         * @param headerName header name
         * @param value      header value
         * @return this
         */
        public Builder with(String headerName, String value) {
            return with(headerName, value, -1);
        }

        /**
         * Include the given header in this builder.
         * <p>
         * This method takes a line number so that a HTTP message parser may query in which lines a
         * certain header appeared later.
         *
         * @param headerName header name
         * @param value      header value
         * @param lineNumber the line number this header appeared in the HTTP message
         * @return this
         */
        public Builder with(String headerName, String value, int lineNumber) {
            char[] upperCaseHeaderNameChars = new char[headerName.length()];
            char[] headerNameChars = headerName.toCharArray();
            for (int i = 0; i < headerNameChars.length; i++) {
                char c = headerNameChars[i];
                if (!FieldValues.isAllowedInTokens(c)) {
                    throw new InvalidHttpHeader("Invalid header name (contains illegal character at index " +
                            i + "): " + headerName, lineNumber);
                }
                // ASCII toUpperCase implementation
                upperCaseHeaderNameChars[i] = ('a' <= c && c <= 'z') ? (char) (c - 32) : c;
            }
            final String upperCaseHeaderName = new String(upperCaseHeaderNameChars);
            FieldValues.indexOfNotAllowedInVCHARs(value).ifPresent((index) -> {
                throw new InvalidHttpHeader("Invalid header value (contains illegal character at index " +
                        index + "): " + value,
                        lineNumber);
            });
            headersByCapitalizedName.computeIfAbsent(upperCaseHeaderName,
                    (ignore) -> new Header(headerName)).values.add(value);
            linesPerHeader.computeIfAbsent(upperCaseHeaderName,
                    (ignore) -> new ArrayList<>(2)).add(lineNumber);
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
            String key = headerName.toUpperCase();
            headersByCapitalizedName.put(key, new Header(headerName, value));
            return this;
        }

        /**
         * Remove the header with the given name (including all values).
         *
         * @param headerName case-insensitive header name
         */
        public void remove(String headerName) {
            headersByCapitalizedName.remove(headerName.toUpperCase());
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
            return new RawHttpHeaders(headersByCapitalizedName, true);
        }

        /**
         * @return the names of all headers added to this builder.
         */
        public List<String> getHeaderNames() {
            return unmodifiableList(headersByCapitalizedName.values().stream()
                    .map(h -> h.originalHeaderName).collect(Collectors.toList()));
        }

        /**
         * @param headerName name of the header (case-insensitive)
         * @return all the line number where the header with the given name appeared in
         */
        public List<Integer> getLineNumbers(String headerName) {
            return linesPerHeader.getOrDefault(headerName.toUpperCase(), Collections.emptyList());
        }

    }

    private static final class Header {
        private final String originalHeaderName;
        private final List<String> values;

        Header(String originalHeaderName) {
            this.originalHeaderName = originalHeaderName;
            this.values = new ArrayList<>(3);
        }

        Header(String originalHeaderName, String value) {
            this(originalHeaderName);
            this.values.add(value);
        }

        Header(String originalHeaderName, List<String> values) {
            this.originalHeaderName = originalHeaderName;
            this.values = values;
        }

        Header freeze() {
            return new Header(originalHeaderName, unmodifiableList(values));
        }

        Header unfreeze() {
            return new Header(originalHeaderName, new ArrayList<>(values));
        }
    }

}
