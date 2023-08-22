package com.athaydes.rawhttp.reqinedit;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Character.isWhitespace;

/**
 * A HTTP file parser.
 * <p>
 * HTTP files are defined by the
 * <a href="https://github.com/JetBrains/http-request-in-editor-spec">http-request-in-editor-spec</a> Specification,
 * as well as the <a href="https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html">
 * IntelliJ Documentation</a>.
 * <p>
 * One a HTTP file is parsed, the list of {@link ReqInEditEntry} produced by it can be run via an instance of
 * {@link ReqInEditUnit}.
 */
public class ReqInEditParser {

    /**
     * Parse the given HTTP file.
     *
     * @param httpFile to parse
     * @return list of entries in the file
     * @throws IOException if an error occurs while reading the file
     */
    public List<ReqInEditEntry> parse(File httpFile) throws IOException {
        return parse(Files.lines(httpFile.toPath()));
    }

    /**
     * Parse the lines of a HTTP file.
     *
     * @param lines to parse
     * @return list of entries in the file
     */
    public List<ReqInEditEntry> parse(Stream<String> lines) {
        List<ReqInEditEntry> entries = new ArrayList<>();
        StringBuilder requestBuilder = new StringBuilder();

        boolean parsingStartLine = true;
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (isComment(line)) {
                if (isSeparator(line)) {
                    if (requestBuilder.length() > 0) {
                        entries.add(maybeParseBody(requestBuilder, iter, false));
                    }
                    parsingStartLine = true;
                }
                continue;
            }
            line = line.trim();
            if (parsingStartLine) {
                if (!line.isEmpty()) {
                    requestBuilder.append(startLine(line));
                    requestBuilder.append('\n');
                    parsingStartLine = false;
                }
            } else if (line.isEmpty()) {
                if (requestBuilder.length() > 0) {
                    entries.add(maybeParseBody(requestBuilder, iter, true));
                    parsingStartLine = true;
                }
            } else {
                requestBuilder.append(line);
                requestBuilder.append('\n');
            }
        }

        if (requestBuilder.length() > 0) {
            entries.add(maybeParseBody(requestBuilder, iter, false));
        }

        return entries;
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
                                          boolean parseBody) {
        String request = requestBuilder.toString();
        ScriptAndResponseRef scriptAndResponseRef = new ScriptAndResponseRef();
        List<StringOrFile> body = Collections.emptyList();
        if (parseBody) {
            // re-use the request builder to read the body
            requestBuilder.delete(0, request.length());
            body = continueFromBody(iter, scriptAndResponseRef);
        }

        return new ReqInEditEntry(request, body,
                scriptAndResponseRef.script, scriptAndResponseRef.responseRef);
    }

    private List<StringOrFile> continueFromBody(
            Iterator<String> iter,
            ScriptAndResponseRef scriptAndResponseRef) {
        List<StringOrFile> result = new ArrayList<>();
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
                if (line.isEmpty()) throw new RuntimeException("Expected file-name after <");
                result.add(StringOrFile.ofFile(line));
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
                    result.add(StringOrFile.ofString(line));
                }
            }
        }
        return trimEmptyLinesAtEnd(result);
    }

    private StringOrFile responseHandler(String line, Iterator<String> iter) {
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
            return StringOrFile.ofString(result.toString());
        } else if (line.isEmpty()) {
            throw new RuntimeException("Expected file name after >");
        } else {
            return StringOrFile.ofFile(line);
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

    private static List<StringOrFile> trimEmptyLinesAtEnd(List<StringOrFile> result) {
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static final class ScriptAndResponseRef {
        @Nullable
        StringOrFile script;
        @Nullable
        String responseRef;
    }

}
