package rawhttp.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OptionsParserTest {

    @Test
    public void cannotParseEmptyArgs() {
        Assertions.assertThrows(OptionsException.class, () ->
                OptionsParser.parse(new String[]{}));
    }

    @Test
    public void canParseHelpOptionWithoutArg() throws OptionsException {
        String[][] examples = new String[][]{
                {"-h"}, {"help"}, {"help", "--help"}, {"-h", "--abc", "--defefe", "-h"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            HelpOptions result = options.run(c -> null, h -> null, s -> null, h -> h);
            assertNotNull(result);
            assertEquals(HelpOptions.GENERAL, result, "Example: " + exampleText);
        }
    }

    @Test
    public void canParseHelpOptionWithArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"help", "send"});
        HelpOptions result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SEND, result);

        options = OptionsParser.parse(new String[]{"help", "run"});
        result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.RUN, result);

        options = OptionsParser.parse(new String[]{"help", "serve"});
        result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SERVE, result);

        options = OptionsParser.parse(new String[]{"help", "whatever"});
        result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.GENERAL, result);

        options = OptionsParser.parse(new String[]{"help", "a", "b", "c"});
        result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.GENERAL, result);

        options = OptionsParser.parse(new String[]{"-h", "send"});
        result = options.run(c -> null, h -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SEND, result);
    }

    @Test
    public void onlyOneOptionCallbackRuns() throws OptionsException {
        final AtomicInteger sendCount = new AtomicInteger(0);
        final AtomicInteger runCount = new AtomicInteger(0);
        final AtomicInteger serveCount = new AtomicInteger(0);
        final AtomicInteger showHelpCount = new AtomicInteger(0);

        Consumer<Options> run = (result) ->
                result.run(c -> sendCount.incrementAndGet(),
                        h -> runCount.incrementAndGet(),
                        s -> serveCount.incrementAndGet(),
                        h -> showHelpCount.incrementAndGet());

        Options result = OptionsParser.parse(new String[]{"send"});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(0, runCount.get());
        assertEquals(0, serveCount.get());
        assertEquals(0, showHelpCount.get());

        result = OptionsParser.parse(new String[]{"run", "req.http"});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(1, runCount.get());
        assertEquals(0, serveCount.get());
        assertEquals(0, showHelpCount.get());

        result = OptionsParser.parse(new String[]{"serve", "."});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(1, runCount.get());
        assertEquals(1, serveCount.get());
        assertEquals(0, showHelpCount.get());

        result = OptionsParser.parse(new String[]{"help"});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(1, runCount.get());
        assertEquals(1, serveCount.get());
        assertEquals(1, showHelpCount.get());
    }

    @Test
    public void onlyOneClientOptionCallbackRuns() throws OptionsException {
        final AtomicInteger serveCount = new AtomicInteger(0);
        final AtomicInteger showHelpCount = new AtomicInteger(0);
        final AtomicInteger runCount = new AtomicInteger(0);
        final AtomicInteger sysinCount = new AtomicInteger(0);
        final AtomicInteger textCount = new AtomicInteger(0);
        final AtomicInteger fileCount = new AtomicInteger(0);

        Consumer<Options> run = (result) ->
                result.run(c -> c.run(o -> sysinCount.incrementAndGet(),
                                (f, o) -> fileCount.incrementAndGet(),
                                (t, o) -> textCount.incrementAndGet()),
                        h -> runCount.incrementAndGet(),
                        s -> serveCount.incrementAndGet(),
                        h -> showHelpCount.incrementAndGet());

        Options result = OptionsParser.parse(new String[]{"send"});
        run.accept(result);

        assertEquals(1, sysinCount.get());
        assertEquals(0, fileCount.get());
        assertEquals(0, textCount.get());

        result = OptionsParser.parse(new String[]{"send", "-f", "."});
        run.accept(result);

        assertEquals(1, sysinCount.get());
        assertEquals(1, fileCount.get());
        assertEquals(0, textCount.get());

        result = OptionsParser.parse(new String[]{"send", "-t", "."});
        run.accept(result);

        assertEquals(1, sysinCount.get());
        assertEquals(1, fileCount.get());
        assertEquals(1, textCount.get());

        assertEquals(0, serveCount.get());
        assertEquals(0, runCount.get());
        assertEquals(0, showHelpCount.get());
    }

    @Test
    public void canParseSendFileOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"send", "-f", "hello"}, {"send", "--file", "hi"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            String expectedFileName = example[2];
            Options options = OptionsParser.parse(example);

            String result = options.run(
                    c -> c.run(o -> "sysin",
                            (f, o) -> f.getName(),
                            (t, o) -> "text"),
                    h -> "run", s -> "server", h -> "help");
            assertEquals("Example: " + exampleText, expectedFileName, result);
        }
    }

    @Test
    public void cannotParseSendFileOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-f"}, {"send", "--file"},
                {"send", "-p", "body", "-f"}, {"send", "--body-file", "my-file", "--file"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void canParseSendRequestTextOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"send", "-t", "hello"}, {"send", "--text", "hi"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            String expectedRequestText = example[2];
            Options options = OptionsParser.parse(example);

            String result = options.run(
                    c -> c.run(o -> "sysin",
                            (f, o) -> "file",
                            (t, o) -> t),
                    h -> "run ", s -> "server", h -> "help");
            assertEquals("Example: " + exampleText,
                    expectedRequestText, result);
        }
    }

    @Test
    public void canParseSendRequestWithRequestLoggingOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"send", "-t", "hello", "-l"}, {"send", "--log-request", "--file", "hi"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            String result = options.run(
                    c -> c.run(o -> "sysin",
                            (f, o) -> "" + o.logRequest,
                            (t, o) -> "" + o.logRequest),
                    h -> "run ", s -> "server", h -> "help");
            assertEquals("Example: " + exampleText,
                    "true", result);
        }

        String[][] noLogExamples = new String[][]{
                {"send", "-t", "hello"}, {"send", "--file", "hi"}
        };

        for (String[] example : noLogExamples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            String result = options.run(
                    c -> c.run(o -> "sysin",
                            (f, o) -> "" + o.logRequest,
                            (t, o) -> "" + o.logRequest),
                    h -> "run ", s -> "server", h -> "help");
            assertEquals("Example: " + exampleText,
                    "false", result);
        }
    }

    @Test
    public void cannotParseSendRequestTextOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-t"}, {"send", "--text"},
                {"send", "-b", "b", "-t"}, {"send", "--body-file", "b", "-p", "body", "--text"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void canParseSendBodyTextOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"send", "-b", "my.body"},
                {"send", "--body-text", "other.body"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            RequestBody result = options.run(
                    c -> c.run(o -> o.getRequestBody().orElseThrow(
                                    () -> new AssertionError("Request body was not present")),
                            (f, o) -> null,
                            (t, o) -> null),
                    h -> null, s -> null, h -> null);

            assertNotNull(result, "Example: " + exampleText);

            String expectedBody = example[2];
            String actualBody = result.run(f -> null, c -> c);
            assertNotNull(actualBody);
            assertEquals("Example: " + exampleText, expectedBody, actualBody);
        }
    }

    @Test
    public void canParseSendBodyFileOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"send", "-g", "my.body"},
                {"send", "--body-file", "other.body"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            RequestBody result = options.run(
                    c -> c.run(o -> o.getRequestBody().orElseThrow(
                                    () -> new AssertionError("Request body was not present")),
                            (f, o) -> null,
                            (t, o) -> null),
                    h -> null, s -> null, h -> null);

            assertNotNull(result, "Example: " + exampleText);

            File expectedBodyFile = new File(example[2]);
            File actualBodyFile = result.run(f -> f, c -> null);
            assertNotNull(actualBodyFile);
            assertEquals(expectedBodyFile, actualBodyFile);
        }
    }

    @Test
    public void cannotParseSendBodyFileOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-g"}, {"send", "--body-file"},
                {"send", "-p", "body", "-b"}, {"send", "--file", "my-file", "--body-file"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void cannotParseSendBodyTextOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-b"}, {"send", "--body-text"},
                {"send", "-p", "body", "-b"}, {"send", "--file", "my-file", "--body-text"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void cannotUseSendBodyTextOptionTwice() {
        String[][] examples = new String[][]{
                {"send", "-b", ".", "-b", "other"},
                {"send", "--body-text", ".", "--body-text", "other"},
                {"send", "-b", ".", "--body-text", "other"},
                {"send", "--print-response-mode", "status", "--body-text", ".", "-b", "other"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "The --body-text option can only be used once", e.getMessage());
            }
        }
    }

    @Test
    public void cannotUseSendBodyFileOptionTwice() {
        String[][] examples = new String[][]{
                {"send", "-g", ".", "-g", "other"},
                {"send", "--body-file", ".", "--body-file", "other"},
                {"send", "-g", ".", "--body-file", "other"},
                {"send", "--print-response-mode", "body", "--body-file", ".", "-g", "other"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "The --body-file option can only be used once", e.getMessage());
            }
        }
    }

    @Test
    public void canAcceptSendRequestTextFromStdin() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"send"});

        SendRequestOptions result = options.run(
                c -> c.run(o -> o,
                        (f, o) -> null,
                        (t, o) -> null),
                h -> null, s -> null, h -> null);
        assertNotNull(result);
        assertFalse(result.getRequestBody().isPresent());
        assertEquals(result.printResponseMode, PrintResponseMode.RESPONSE);
    }

    @Test
    public void cannotUseSendFileOptionTwice() {
        String[][] examples = new String[][]{
                {"send", "-f", ".", "-f", "other"},
                {"send", "--file", ".", "--file", "other"},
                {"send", "-f", ".", "--file", "other"},
                {"send", "--print-response-mode", "status", "--file", ".", "-f", "other"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "the --file option can only be used once", e.getMessage());
            }
        }
    }

    @Test
    public void cannotParseSendUsingBothFileAndText() {
        try {
            Options options = OptionsParser.parse(new String[]{
                    "send", "-t", "REQ", "-f", "file.req"
            });
            fail("Should have failed to accept both -f and -t options together but got: " + options);
        } catch (OptionsException e) {
            assertEquals("Cannot use both --text and --file options together", e.getMessage());
        }

        try {
            Options options = OptionsParser.parse(new String[]{
                    "send", "--file", "file", "-p", "body", "--text", "text.req"
            });
            fail("Should have failed to accept both -f and -t options together but got: " + options);
        } catch (OptionsException e) {
            assertEquals("Cannot use both --text and --file options together", e.getMessage());
        }
    }

    @Test
    public void canParseSendWithAllOptions() throws OptionsException {
        @SuppressWarnings("WeakerAccess")
        class Res {
            final File requestFile;
            final String requestText;
            final SendRequestOptions options;

            Res(File requestFile, String requestText, SendRequestOptions options) {
                this.requestFile = requestFile;
                this.requestText = requestText;
                this.options = options;
            }
        }

        Options options = OptionsParser.parse(new String[]{"send", "-t", "REQ", "-p", "all", "-g", "BODY"});
        ClientOptions clientOptions = options.run(c -> c, h -> null, s -> null, h -> null);
        assertNotNull(clientOptions, "Parsed client options");

        Res result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        RequestBody requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("BODY"), requestBody.run(f -> f, t -> null));
        assertEquals(result.options.printResponseMode, PrintResponseMode.ALL);
        assertFalse(result.options.logRequest);
        assertEquals("REQ", result.requestText);
        assertNull(result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "-f", "REQ", "-p", "body", "-g", "b.js", "-l"});
        clientOptions = options.run(c -> c, h -> null, s -> null, h -> null);
        assertNotNull(clientOptions, "Parsed client options");

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("b.js"), requestBody.run(f -> f, t -> null));
        assertEquals(result.options.printResponseMode, PrintResponseMode.BODY);
        assertTrue(result.options.logRequest);
        assertNull(result.requestText);
        assertEquals(new File("REQ"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--log-request", "--body-file", "body.js", "--file", "file.req"});
        clientOptions = options.run(c -> c, h -> null, s -> null, h -> null);
        assertNotNull(clientOptions, "Parsed client options");

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("body.js"), requestBody.run(f -> f, t -> null));
        assertEquals(result.options.printResponseMode, PrintResponseMode.RESPONSE);
        assertTrue(result.options.logRequest);
        assertNull(result.requestText);
        assertEquals(new File("file.req"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--body-text", "Hello", "--file", "file2.req"});
        clientOptions = options.run(c -> c, h -> null, s -> null, h -> null);
        assertNotNull(clientOptions, "Parsed client options");

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals("Hello", requestBody.run(f -> null, t -> t));
        assertEquals(result.options.printResponseMode, PrintResponseMode.RESPONSE);
        assertFalse(result.options.logRequest);
        assertNull(result.requestText);
        assertEquals(new File("file2.req"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--body-file", "my.body", "-l", "--file", "file2.req"});
        clientOptions = options.run(c -> c, h -> null, s -> null, h -> null);
        assertNotNull(clientOptions, "Parsed client options");

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("my.body"), requestBody.run(f -> f, t -> null));
        assertEquals(result.options.printResponseMode, PrintResponseMode.RESPONSE);
        assertTrue(result.options.logRequest);
        assertNull(result.requestText);
        assertEquals(new File("file2.req"), result.requestFile);
    }

    @Test
    public void canParseRunOptionWithoutArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"run", "file.http"});

        HttpFileOptions result = options.run(c -> null, h -> h, s -> null, h -> null);

        assertNotNull(result, "Parsed run options");
        assertEquals(new File("file.http"), result.httpFile);
        assertNull(result.cookieJar);
        assertNull(result.envName);
        assertFalse(result.logRequest);
        assertEquals(PrintResponseMode.RESPONSE, result.printResponseMode);
    }

    @Test
    public void canParseRunOptionWithArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"run", "file.http", "-l",
                "-e", "my-env", "-c", "cookies.txt", "-p", "stats"});

        HttpFileOptions result = options.run(c -> null, h -> h, s -> null, h -> null);

        assertNotNull(result, "Parsed run options");
        assertEquals(new File("file.http"), result.httpFile);
        assertEquals(new File("cookies.txt"), result.cookieJar);
        assertEquals("my-env", result.envName);
        assertTrue(result.logRequest);
        assertEquals(PrintResponseMode.STATS, result.printResponseMode);
    }

    @Test
    public void cannotParseRunMissingArgs() {
        String[][] examples = new String[][]{
                {"run", "file", "-e"}, {"run", "file", "--cookiejar"},
                {"run", "file", "-e", "env", "-c"}, {"run", "file", "-e", "env", "--cookiejar"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void cannotParseRunOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"run", "file", "-e"}, {"run", "file", "--cookiejar"},
                {"run", "file", "-e", "env", "-c"}, {"run", "file", "-e", "env", "--cookiejar"}
        };

        assertMissingArgumentError(examples);
    }

    @Test
    public void canParseServerOptionWithoutOptions() throws OptionsException {
        String[][] examples = new String[][]{
                {"serve", "."}, {"serve", "my/path"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

            assertNotNull(result, "Parsed server options. Example: " + exampleText);
            assertFalse(result.logRequests, "Example: " + exampleText);
            assertEquals(ServerOptions.DEFAULT_SERVER_PORT, result.port);

            File expectedDir = new File(example[1]);
            assertEquals(expectedDir, result.dir, "Example: " + exampleText);
        }
    }

    @Test
    public void canParseServerAbsolutePath() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"serve", "/temp/dir"});
        ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

        assertNotNull(result, "Parsed server options");
        assertFalse(result.logRequests);
        assertEquals(ServerOptions.DEFAULT_SERVER_PORT, result.port);
        File expectedDir = new File("/temp/dir");
        assertEquals(expectedDir, result.dir);

    }

    @Test
    public void canParseServeWithPortOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"serve", "boo", "-p", "8888"}, {"serve", "another/path", "--port", "1234"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

            assertNotNull(result, "Parsed server options. Example: " + exampleText);
            assertFalse(result.logRequests, "Example: " + exampleText);

            File expectedDir = new File(example[1]);
            assertEquals(expectedDir, result.dir);

            int expectedPort = Integer.parseInt(example[3]);
            assertEquals(expectedPort, result.port);
        }
    }

    @Test
    public void canParseServeWithRootPathOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"serve", "boo", "-r", "abc"}, {"serve", "another/path", "--root-path", "/def/ghi"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

            File expectedDir = new File(example[1]);
            assertEquals(expectedDir, result.dir);

            assertNotNull(result, "Parsed server options. Example: " + exampleText);
            assertEquals(example[3], result.rootPath);
        }
    }

    @Test
    public void canParseServeWithMediaTypesOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"serve", "boo", "-m", "MEDIA"},
                {"serve", "another/path", "--media-types", "OTHER_MEDIA"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

            assertNotNull(result, "Parsed server options. Example: " + exampleText);
            assertFalse(result.logRequests, "Example: " + exampleText);

            File expectedMediaFile = new File(example[3]);
            assertEquals(expectedMediaFile, result.getMediaTypesFile().orElseThrow(() ->
                    new AssertionError("Media Types file is not present")));
        }
    }

    @Test
    public void canParseServerOptionWithAllArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"serve", "something", "-p", "33", "-l", "-r", "/hi"});
        ServerOptions result = options.run(c -> null, h -> null, s -> s, h -> null);

        assertNotNull(result, "Parsed server options");
        assertTrue(result.logRequests);
        assertEquals(new File("something"), result.dir);
        assertEquals(33, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());
        assertEquals("/hi", result.rootPath);

        options = OptionsParser.parse(new String[]{"serve", "my/files", "-p", "44", "--log-requests", "--root-path", "ho"});
        result = options.run(c -> null, h -> null, s -> s, h -> null);

        assertNotNull(result, "Parsed server options");
        assertTrue(result.logRequests);
        assertEquals(new File("my/files"), result.dir);
        assertEquals(44, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());
        assertEquals("ho", result.rootPath);

        options = OptionsParser.parse(new String[]{"serve", "/abs/path", "--log-requests", "-p", "55"});
        result = options.run(c -> null, h -> null, s -> s, h -> null);

        assertNotNull(result, "Parsed server options");
        assertTrue(result.logRequests);
        assertEquals(new File("/abs/path"), result.dir);
        assertEquals(55, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());
        assertEquals("", result.rootPath);

        options = OptionsParser.parse(new String[]{"serve", "/temp", "-l", "-r", "/abc/def",
                "--port", "66", "--media-types", "media.properties"});
        result = options.run(c -> null, h -> null, s -> s, h -> null);

        assertNotNull(result, "Parsed server options");
        assertTrue(result.logRequests);
        assertEquals(new File("/temp"), result.dir);
        assertEquals(66, result.port);
        assertEquals(new File("media.properties"), result.getMediaTypesFile().orElseThrow(() ->
                new AssertionError("Media Types file is not present")));
        assertEquals("/abc/def", result.rootPath);
    }

    @Test
    public void cannotUseServerPortOptionTwice() {
        String[][] examples = new String[][]{
                {"serve", ".", "-p", "1", "-p", "2"},
                {"serve", ".", "--port", "1", "-l", "-p", "2"},
                {"serve", ".", "-l", "-p", "1", "--port", "2"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "the --port option can only be used once", e.getMessage());
            }
        }
    }

    @Test
    public void cannotParseServeRootPathOptionMissingMandatoryArg() {
        try {
            OptionsParser.parse(new String[]{"run"});
            fail("Should not be able to parse run command without options");
        } catch (OptionsException e) {
            assertEquals("No http requests file provided", e.getMessage());
        }
    }

    @Test
    public void doesNotAcceptUnrecognizedOptions() {
        BiConsumer<String[], String> assertExampleFails = (example, invalidOption) -> {
            String exampleText = Arrays.toString(example);
            try {
                Options options = OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText + ": Result = " + options);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "Unrecognized option: " + invalidOption, e.getMessage());
            }
        };

        assertExampleFails.accept(new String[]{"serve", ".", "other"}, "other");
        assertExampleFails.accept(new String[]{"serve", ".", "-z"}, "-z");
        assertExampleFails.accept(new String[]{"serve", ".", "-z", "--zooo"}, "-z");
        assertExampleFails.accept(new String[]{"serve", ".", "-p", "80", "--zooo"}, "--zooo");
        assertExampleFails.accept(new String[]{"send", "."}, ".");
        assertExampleFails.accept(new String[]{"send", "-z"}, "-z");
        assertExampleFails.accept(new String[]{"send", "-p", "body", "-f", "file", "-z", "-p"}, "-z");
    }

    private static void assertMissingArgumentError(String[][] examples) {
        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);
            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText, "Missing argument for " +
                        example[example.length - 1] +
                        " flag", e.getMessage());
            }
        }
    }

}
