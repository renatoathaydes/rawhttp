package com.athaydes.rawhttp.reqinedit;

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

    List<ReqInEditEntry> parse(File file) throws IOException {
        return parse(Files.lines(file.toPath()));
    }

    List<ReqInEditEntry> parse(Stream<String> lines) {
        List<ReqInEditEntry> result = new ArrayList<>();
        StringBuilder requestBuilder = new StringBuilder();

        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (!line.startsWith(" ") && requestBuilder.length() > 0) {
                requestBuilder.append('\n');
            }
            if (isComment(line)) {
                if (isSeparator(line)) {
                    if (requestBuilder.length() > 0) {
                        result.add(maybeParseBody(requestBuilder, iter, false));
                    }
                }
                continue;
            }
            line = line.trim();
            if (line.isEmpty()) {
                if (requestBuilder.length() > 0) {
                    result.add(maybeParseBody(requestBuilder, iter, true));
                }
            } else {
                requestBuilder.append(line);
            }
        }

        if (requestBuilder.length() > 0) {
            result.add(maybeParseBody(requestBuilder, iter, false));
        }

        return result;
    }

    private ReqInEditEntry maybeParseBody(StringBuilder requestBuilder,
                                          Iterator<String> iter,
                                          boolean parseBody) {
        ReqWriter reqWriter = new ReqWriter();
        reqWriter.write(requestBuilder.toString().trim().getBytes(StandardCharsets.UTF_8));
        reqWriter.writeln();
        @Nullable String script = parseBody ? continueFromBody(iter, reqWriter) : null;
        requestBuilder.delete(0, requestBuilder.length());
        return new ReqInEditEntry(reqWriter.toRequest(), script);
    }

    private String continueFromBody(Iterator<String> iter,
                                    ReqWriter reqWriter) {
        // line separating headers from body
        reqWriter.writeln();

        @Nullable String script = null;
        boolean foundNonWhitespace = false;

        while (iter.hasNext()) {
            String line = iter.next();
            if (isComment(line)) {
                if (isSeparator(line)) break;
                else continue;
            }
            if (line.startsWith("> ")) {
                foundNonWhitespace = true;
                line = line.substring(2).trim();
                script = responseHandler(line, iter);
                // FIXME maybe ended request, maybe there's a response ref
                break;
            } else if (line.startsWith("< ")) {
                foundNonWhitespace = true;
                line = line.substring(2).trim();
                byte[] bytes = inputFile(line);
                reqWriter.write(bytes);
                reqWriter.writeln();
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

        return script;
    }

    private byte[] inputFile(String path) {
        try {
            return fileReader.read(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String responseHandler(String line, Iterator<String> iter) {
        throw new UnsupportedOperationException("responseHandler");
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

    private static final class ReqWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // remember the tail Strings written to this writer as we need to trim it at the end
        private final StringBuilder tail = new StringBuilder();

        void write(CharSequence chars) {
            String text = chars.toString();
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

}
