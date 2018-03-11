package com.athaydes.rawhttp.core;

import java.io.IOException;

@FunctionalInterface
interface IOConsumer<T> {
    void accept(T t) throws IOException;
}
