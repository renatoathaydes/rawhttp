package com.athaydes.rawhttp.reqinedit;

import com.athaydes.rawhttp.reqinedit.js.JsEnvironment;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.body.BytesBody;
import rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Character.isWhitespace;

public class ReqInEditParser {

    private static final RawHttp HTTP = new RawHttp(RawHttpOptions.newBuilder()
            .allowComments()
            .allowIllegalStartLineCharacters()
            .build());

    private final FileReader fileReader;

    public ReqInEditParser() {
        this(new DefaultFileReader());
    }

    public ReqInEditParser(FileReader fileReader) {
        this.fileReader = fileReader;
    }

    public ReqInEditUnit parse(File httpFile) throws IOException {
        return parse(httpFile, null);
    }

    public ReqInEditUnit parse(File httpFile, @Nullable String environmentName) throws IOException {
        return parse(Files.lines(httpFile.toPath()), loadEnvironment(httpFile, environmentName));
    }

    ReqInEditUnit parse(Stream<String> lines) {
        return parse(lines, loadEnvironment(null, null));
    }

    ReqInEditUnit parse(Stream<String> lines,
                        HttpEnvironment environment) {
        List<ReqInEditEntry> entries = new ArrayList<>();
        StringBuilder requestBuilder = new StringBuilder();

        boolean parsingStartLine = true;
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (!line.startsWith(" ") && requestBuilder.length() > 0) {
                requestBuilder.append('\n');
            }
            if (isComment(line)) {
                if (isSeparator(line)) {
                    if (requestBuilder.length() > 0) {
                        entries.add(maybeParseBody(requestBuilder, iter, environment, false));
                    }
                    parsingStartLine = true;
                }
                continue;
            }
            line = line.trim();
            if (parsingStartLine) {
                if (!line.isEmpty()) {
                    requestBuilder.append(startLine(line));
                    parsingStartLine = false;
                }
            } else if (line.isEmpty()) {
                if (requestBuilder.length() > 0) {
                    entries.add(maybeParseBody(requestBuilder, iter, environment, true));
                    parsingStartLine = true;
                }
            } else {
                requestBuilder.append(line);
            }
        }

        if (requestBuilder.length() > 0) {
            entries.add(maybeParseBody(requestBuilder, iter, environment, false));
        }

        return new ReqInEditUnit(entries, environment);
    }

    private static String startLine(String line) {
        int i = line.indexOf(' ');
        if (i > 0) {
            String method = line.substring(0, i);
            switch (method) {
                case "GET":
                case "HEAD":
                case "POST":
                case "PUT":
                case "DELETE":
                case "CONNECT":
                case "PATCH":
                case "OPTIONS":
                case "TRACE":
                    // line is already fine
                    return line;
            }
        }
        // use the default method
        return "GET " + line;
    }

    private ReqInEditEntry maybeParseBody(StringBuilder requestBuilder,
                                          Iterator<String> iter,
                                          HttpEnvironment environment,
                                          boolean parseBody) {
        ReqWriter reqWriter = new ReqWriter(environment);
        reqWriter.write(requestBuilder.toString().trim());
        reqWriter.writeln();
        ScriptAndResponseRef scriptAndResponseRef = new ScriptAndResponseRef();
        if (parseBody) {
            continueFromBody(iter, reqWriter, scriptAndResponseRef);
        }
        requestBuilder.delete(0, requestBuilder.length());
        return new ReqInEditEntry(reqWriter.toRequest(),
                scriptAndResponseRef.script, scriptAndResponseRef.responseRef);
    }

    private void continueFromBody(Iterator<String> iter,
                                  ReqWriter reqWriter,
                                  ScriptAndResponseRef scriptAndResponseRef) {
        // line separating headers from body
        reqWriter.writeln();

        boolean foundNonWhitespace = false;
        boolean doneParsingBody = false;

        while (iter.hasNext()) {
            String line = iter.next();
            if (isComment(line)) {
                if (isSeparator(line)) break;
                else continue;
            }
            if (!doneParsingBody && line.startsWith("> ")) {
                foundNonWhitespace = true;
                line = line.substring(2).trim();
                scriptAndResponseRef.script = responseHandler(line, iter);
                doneParsingBody = true; // can only parse a response-ref now
            } else if (!doneParsingBody && line.startsWith("< ")) {
                foundNonWhitespace = true;
                line = line.substring(2).trim();
                byte[] bytes = inputFile(line);
                reqWriter.write(bytes);
                reqWriter.writeln();
            } else if (line.startsWith("<> ")) {
                String ref = line.substring(3).trim();
                if (!ref.isEmpty()) {
                    scriptAndResponseRef.responseRef = ref;
                }
                doneParsingBody = true;
            } else if (doneParsingBody) {
                line = line.trim();
                if (!line.isEmpty()) {
                    throw new RuntimeException("Unexpected token after response: " + line);
                }
            } else {
                // trim any leading whitespaces
                if (!foundNonWhitespace) {
                    line = trimLeft(line);
                    foundNonWhitespace = !line.isEmpty();
                }
                if (foundNonWhitespace) {
                    reqWriter.write(line);
                    reqWriter.writeln();
                }
            }
        }
    }

    private byte[] inputFile(String path) {
        try {
            return fileReader.read(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String responseHandler(String line, Iterator<String> iter) {
        if (line.startsWith("{%")) {
            line = line.substring(2);
            StringBuilder result = new StringBuilder();
            while (!line.contains("%}")) {
                result.append(line).append('\n');
                if (!iter.hasNext()) break;
                line = iter.next();
            }
            int endIndex = line.indexOf("%}");
            if (endIndex > 0) {
                result.append(line, 0, endIndex);
            }
            line = line.substring(endIndex + 2).trim();
            if (!line.isEmpty()) {
                throw new RuntimeException("Unexpected token after response handler: " + line);
            }
            return result.toString();
        } else {
            return new String(inputFile(line), StandardCharsets.UTF_8);
        }
    }

    private static boolean isComment(String line) {
        return line.startsWith("//") || line.startsWith("#");
    }

    private static boolean isSeparator(String line) {
        return line.startsWith("###");
    }

    private static String trimLeft(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!isWhitespace(line.charAt(i))) {
                return line.substring(i);
            }
        }
        return "";
    }

    static JsEnvironment loadEnvironment(@Nullable File httpFile,
                                         @Nullable String name) {
        return new JsEnvironment(httpFile == null ? null : httpFile.getParentFile(), name);
    }

    private static final class ReqWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // remember the tail Strings written to this writer as we need to trim it at the end
        private final StringBuilder tail = new StringBuilder();

        private final HttpEnvironment environment;

        public ReqWriter(HttpEnvironment environment) {
            this.environment = environment;
        }

        void write(CharSequence chars) {
            String text = environment.renderTemplate(chars.toString());
            tail.append(text);
            try {
                bytes.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // never happens
                throw new RuntimeException(e);
            }
        }

        public void writeln() {
            bytes.write('\n');
            tail.append('\n');
        }

        void write(byte[] b) {
            try {
                bytes.write(b);
            } catch (IOException e) {
                // never happens
                throw new RuntimeException(e);
            } finally {
                // every time bytes are written, we don't need to do any trimming at the end
                tail.delete(0, tail.length());
            }
        }

        RawHttpRequest toRequest() {
            int len = bytes.size() - whitespacesInTail();
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes.toByteArray(), 0, len);
            try {
                RawHttpRequest req = HTTP.parseRequest(stream);

                // because body headers are missing, we need to read the body separately
                if (stream.available() > 0) {
                    req = req.withBody(readBody(stream));
                }
                return req;
            } catch (IOException e) {
                // never happens
                throw new RuntimeException(e);
            }
        }

        private int whitespacesInTail() {
            int count = 0;
            for (int i = tail.length() - 1; i >= 0; i--) {
                if (isWhitespace(tail.charAt(i))) {
                    count++;
                } else {
                    return count;
                }
            }
            return count;
        }

        @Nullable
        private static HttpMessageBody readBody(ByteArrayInputStream stream) throws IOException {
            byte[] bytes = new byte[stream.available()];
            //noinspection ResultOfMethodCallIgnored
            stream.read(bytes);
            if (allBytes(bytes, ' ', '\n')) {
                return null;
            }
            return new BytesBody(bytes);
        }

        private static boolean allBytes(byte[] bytes, char... chars) {
            for (byte b : bytes) {
                for (char ch : chars) {
                    if (ch != b) return false;
                }
            }
            return true;
        }
    }

    private static final class DefaultFileReader implements FileReader {
        @Override
        public byte[] read(String path) throws IOException {
            return Files.readAllBytes(Paths.get(path));
        }
    }

    private static final class ScriptAndResponseRef {
        @Nullable
        String script;
        @Nullable
        String responseRef;
    }

}
