package com.athaydes.rawhttp.core;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;

public class RawHttp {

    public final RawHttpRequest parseRequest(String request) {
        try {
            return parseRequest(new StringReader(request));
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    public final RawHttpRequest parseRequest(File file, Charset charset) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseRequest(stream, charset).eagerly();
        }
    }

    public final RawHttpRequest parseRequest(InputStream inputStream, Charset charset) throws IOException {
        return parseRequest(new InputStreamReader(inputStream, charset));
    }

    public RawHttpRequest parseRequest(Reader reader) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(reader);

        String line = bufferedReader.readLine();

        if (line != null) {
            MethodLine methodLine = parseMethodLine(line);
            Map<String, Collection<String>> headers = parseHeaders(bufferedReader);
            if (headers.containsKey("Host")) {
                methodLine = verifyHost(methodLine, headers.get("Host"));
            }
            BodyReader bodyReader = parseBody(bufferedReader);
            return new RawHttpRequest(methodLine, headers, bodyReader);
        } else {
            throw new InvalidHttpRequest("No content", 0);
        }
    }

    private MethodLine verifyHost(MethodLine methodLine, Collection<String> host) {
        if (host.isEmpty()) {
            if (methodLine.getUri().getHost() == null) {
                throw new InvalidHttpRequest("Host not given neither in method line nor Host header", 1);
            }
            return methodLine;
        } else if (host.size() == 1) {
            return methodLine.withHost(host.iterator().next());
        } else {
            throw new InvalidHttpRequest("More than one Host header specified", 2);
        }
    }

    public MethodLine parseMethodLine(String methodLine) {
        if (methodLine.isEmpty()) {
            throw new InvalidHttpRequest("Empty method line", 1);
        } else {
            String[] parts = methodLine.split("\\s+");
            if (parts.length == 2 || parts.length == 3) {
                String method = parts[0];
                URI uri = createUri(parts[1]);
                String version = parts.length == 3 ? parts[2] : "HTTP/1.1";
                return new MethodLine(method, uri, version);
            } else {
                throw new InvalidHttpRequest("Invalid method line", 1);
            }
        }
    }

    private URI createUri(String part) {
        if (!part.startsWith("http")) {
            part = "http://" + part;
        }
        URI uri;
        try {
            uri = new URI(part);
        } catch (URISyntaxException e) {
            throw new InvalidHttpRequest("Invalid URI: " + e.getMessage(), 1);
        }
        return uri;
    }

    private Map<String, Collection<String>> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, Collection<String>> result = new HashMap<>();
        int lineNumber = 2;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":", 2);
            if (parts.length != 2) {
                throw new InvalidHttpRequest("Invalid header", lineNumber);
            }
            result.computeIfAbsent(parts[0].trim(), (ignore) -> new ArrayList<>(3)).add(parts[1].trim());
        }

        return Collections.unmodifiableMap(result);
    }

    private BodyReader parseBody(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        if (builder.length() == 0) {
            return null;
        } else {
            return new EagerBodyReader(builder.toString().getBytes());
        }
    }

}
