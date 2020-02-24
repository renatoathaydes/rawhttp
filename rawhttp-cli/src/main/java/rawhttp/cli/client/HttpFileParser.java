package rawhttp.cli.client;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RequestLine;
import rawhttp.core.body.BytesBody;
import rawhttp.core.errors.InvalidHttpRequest;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class HttpFileParser {
    public static final Random RANDOM = new Random();
    private final RawHttp http;

    public HttpFileParser(RawHttp http) {
        this.http = http;
    }

    public List<HttpFileEntry> parse(File httpFile, @Nullable File envFile) throws IOException {
        return parse(new FileInputStream(httpFile), envFile == null ? null : new FileInputStream(envFile));
    }

    public List<HttpFileEntry> parse(InputStream httpFile, @Nullable InputStream envFile) throws IOException {
        BufferedInputStream httpStream = new BufferedInputStream(httpFile, 4096);
        return splitInput(httpStream, new HashMap<>());
    }

    private List<HttpFileEntry> splitInput(InputStream input, Map<String, Object> env) throws IOException {
        List<HttpFileEntry> result = new ArrayList<>(4);
        PushbackInputStream stream = new PushbackInputStream(input, 1);
        StringBuilder builder = new StringBuilder();

        boolean readingComment = false;
        boolean startingNewLine = true;

        int b;
        while ((b = stream.read()) >= 0) {
            if (readingComment) {
                // ignore anything else within comments
                if (b == '\n') {
                    builder.append('\n');
                    startingNewLine = true;
                    readingComment = false;
                    continue;
                }
            } else if (b == '\n') {
                builder.append('\n');
                if (startingNewLine) {
                    // empty line indicates beginning of request body, possibly
                    readingComment = parseBodyOrRequestEnd(stream, builder.toString().trim(), result);
                    builder.delete(0, builder.length());
                }
                startingNewLine = true;
                continue;
            } else if (b == '{') {
                // may be a variable
                b = stream.read();
                if (b == '{') {
                    String variable = parseVariable(stream);
                    builder.append(resolveVariable(variable, env));
                } else {
                    // the first '{' is just a normal char... put the new 'b' back!
                    builder.append('{');
                    stream.unread(b);
                }
            } else if (startingNewLine && b == '#') {
                readingComment = true;
            } else {
                builder.append((char) b);
            }
            startingNewLine = false;
        }

        parseBodyOrRequestEnd(stream, builder.toString().trim(), result);

        return result;
    }

    // must return whether we're reading comments after this returns
    private boolean parseBodyOrRequestEnd(PushbackInputStream stream,
                                          String requestWithoutBody,
                                          List<HttpFileEntry> result) throws IOException {
        if (requestWithoutBody.isEmpty()) {
            return false;
        }

        String[] requestParts = requestWithoutBody.split("\n", 2);

        RequestLine requestLine = http.getMetadataParser().parseRequestLine(requestParts[0]);
        RawHttpHeaders headers = requestParts.length < 2
                ? RawHttpHeaders.empty()
                : http.getMetadataParser().parseHeaders(
                new ByteArrayInputStream(requestParts[1].getBytes(StandardCharsets.US_ASCII)),
                (message, lineNumber) ->
                        // add 1 to the line number to correct for the start-line
                        new InvalidHttpRequest(message, lineNumber + 1));

        // because the metadata-parser will consume a new-line after the headers, we need to put one back here
        // to make the parser below see that the next character starts a new line, conceptually at least
        stream.unread('\n');

        ByteArrayOutputStream body = new ByteArrayOutputStream(1024);
        boolean readingComment = false;
        int b;
        byte[] minBuffer = new byte[3];
        while ((b = stream.read()) >= 0) {
            // we only look for the request delimiter on the very start of a new line
            if (b == '\n') {
                int count = stream.read(minBuffer);
                if (count == 3 && Arrays.equals(minBuffer, new byte[]{'#', '#', '#'})) {
                    // we've got to the request delimiter! now just check if we're reading a comment next
                    b = stream.read();
                    readingComment = b != '\n';
                    break;
                } else if (count > 0) {
                    // false alarm, this is still the body bytes
                    body.write(minBuffer, 0, count);
                }
            } else {
                body.write(b);
            }
        }

        RawHttpRequest rawHttpRequest = new RawHttpRequest(requestLine, headers, null, null);
        if (body.size() > 0 || RawHttp.requestHasBody(headers)) {
            rawHttpRequest = rawHttpRequest.withBody(new BytesBody(body.toByteArray()), true);
        }

        result.add(new HttpFileEntry(rawHttpRequest, null));

        return readingComment;
    }

    private static String parseVariable(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        int b;
        while ((b = stream.read()) >= 0) {
            if (b == '}') {
                // may end a variable
                b = stream.read();
                if (b == '}') {
                    break; // done
                } else {
                    // the first '}' is just a normal char...
                    builder.append('}');
                    if (b > 0) builder.append(b);
                }
            }
        }
        return builder.toString().trim();
    }

    private static Object resolveVariable(String variable, Map<String, Object> env) {
        if (variable.equals("$uuid")) {
            return UUID.randomUUID().toString();
        }
        if (variable.equals("$timestamp")) {
            return System.currentTimeMillis();
        }
        if (variable.equals("$randomInt")) {
            return RANDOM.nextInt(1001);
        }
        return env.getOrDefault(variable, "");
    }

}
