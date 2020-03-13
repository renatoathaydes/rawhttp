package com.athaydes.rawhttp.reqinedit;

import java.util.Objects;
import java.util.function.Function;

public final class StringOrFile {
    private final String data;
    private final boolean isFile;

    private StringOrFile(String data, boolean isFile) {
        this.data = data;
        this.isFile = isFile;
    }

    static StringOrFile ofString(String bodyPart) {
        return new StringOrFile(bodyPart, false);
    }

    static StringOrFile ofFile(String file) {
        return new StringOrFile(file, true);
    }

    <T> T match(Function<String, T> onString, Function<String, T> onFile) {
        if (isFile) return onFile.apply(data);
        else return onString.apply(data);
    }

    public boolean isBlank() {
        return match(StringOrFile::blank, StringOrFile::blank);
    }

    private static boolean blank(String text) {
        return text.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "StringOrFile{" +
                "data='" + data + '\'' +
                ", isFile=" + isFile +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringOrFile that = (StringOrFile) o;
        return isFile == that.isFile &&
                data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, isFile);
    }
}
