package com.athaydes.rawhttp.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

public class RawHttpHeaders {

    private final List<String> headerNames;
    private final Map<String, List<String>> valuesByCapitalizedName;

    private RawHttpHeaders(List<String> headerNames,
                           Map<String, List<String>> valuesByCapitalizedName) {
        this.headerNames = unmodifiableList(headerNames);
        this.valuesByCapitalizedName = unmodifiableMap(valuesByCapitalizedName);
    }

    public List<String> get(String headerName) {
        return valuesByCapitalizedName.getOrDefault(headerName.toUpperCase(), emptyList());
    }

    public Optional<String> getFirst(String headerName) {
        List<String> values = get(headerName);
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(values.get(0));
        }
    }

    public List<String> getHeaderNames() {
        return headerNames;
    }

    public Set<String> getUniqueHeaderNames() {
        return valuesByCapitalizedName.keySet();
    }

    public boolean contains(String headerName) {
        return getUniqueHeaderNames().contains(headerName.toUpperCase());
    }

    public Map<String, List<String>> asMap() {
        return unmodifiableMap(valuesByCapitalizedName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawHttpHeaders that = (RawHttpHeaders) o;

        return valuesByCapitalizedName.equals(that.valuesByCapitalizedName);
    }

    public void forEach(BiConsumer<String, String> consumer) {
        Map<String, Integer> currentIndex = new HashMap<>(valuesByCapitalizedName.size());
        for (String name : headerNames) {
            String key = name.toUpperCase();
            currentIndex.merge(key, 0, (a, b) -> a + 1);
            String value = valuesByCapitalizedName.get(key).get(currentIndex.get(key));
            consumer.accept(name, value);
        }
    }

    @Override
    public int hashCode() {
        return valuesByCapitalizedName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        forEach((name, value) -> {
            builder.append(name).append(": ").append(value).append("\r\n");
        });
        return builder.append("\r\n").toString();
    }

    public static class Builder {

        public static Builder newBuilder(RawHttpHeaders headers) {
            Builder builder = new Builder();
            builder.valuesByCapitalizedName.putAll(headers.valuesByCapitalizedName);
            builder.headerNames.addAll(headers.headerNames);
            return builder;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static RawHttpHeaders emptyRawHttpHeaders() {
            return new RawHttpHeaders(emptyList(), emptyMap());
        }

        private Builder() {
            // hide
        }

        private final List<String> headerNames = new ArrayList<>();
        private final Map<String, List<String>> valuesByCapitalizedName = new HashMap<>();

        public Builder with(String headerName, String value) {
            headerNames.add(headerName);
            valuesByCapitalizedName.computeIfAbsent(headerName.toUpperCase(),
                    (ignore) -> new ArrayList<>(3)).add(value);
            return this;
        }

        public RawHttpHeaders build() {

            return new RawHttpHeaders(headerNames, valuesByCapitalizedName);
        }

        public Builder overwrite(String headerName, String value) {
            headerNames.add(headerName);
            valuesByCapitalizedName.put(headerName.toUpperCase(), singletonList(value));
            return this;
        }

        public Builder merge(RawHttpHeaders headers) {
            headers.forEach(this::with);
            return this;
        }

        public List<String> getHeaderNames() {
            return unmodifiableList(headerNames);
        }
    }

}
