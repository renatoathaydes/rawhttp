package com.athaydes.rawhttp.reqinedit;

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
}
