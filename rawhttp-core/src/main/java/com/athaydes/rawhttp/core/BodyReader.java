package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.io.InputStream;

public interface BodyReader {

    BodyReader eager() throws IOException;

    byte[] asBytes() throws IOException;

    InputStream asStream();

}
