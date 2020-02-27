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
                    requestBuilder.append('\n');
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
        reqWriter.write(requestBuilder.toString().getBytes(StandardCharsets.UTF_8));
        @Nullable String script = parseBody ? continueFromBody(iter, reqWriter) : null;
        requestBuilder.delete(0, requestBuilder.length());
        return new ReqInEditEntry(reqWriter.toRequest(), script);
    }

    private String continueFromBody(Iterator<String> iter,
                                    ReqWriter reqWriter) {
        @Nullable String script = null;

        while (iter.hasNext()) {
            String line = iter.next();
            if (isComment(line)) {
                if (isSeparator(line)) break;
                else continue;
            }
            line = line.trim();
            if (line.startsWith("> ")) {
                line = line.substring(2).trim();
                script = responseHandler(line, iter);
                // FIXME maybe ended request, maybe there's a response ref
                break;
            } else if (line.startsWith("< ")) {
                line = line.substring(2).trim();
                byte[] bytes = inputFile(line);
                reqWriter.write(bytes);
            } else {
                reqWriter.write(line);
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

    private static final class ReqWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        void write(CharSequence chars) {
            try {
                bytes.write(chars.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // never happens
                throw new RuntimeException(e);
            }
        }

        void write(byte[] b) {
            try {
                bytes.write(b);
            } catch (IOException e) {
                // never happens
                throw new RuntimeException(e);
            }
        }

        RawHttpRequest toRequest() {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes.toByteArray());
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
