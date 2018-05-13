package com.athaydes.rawhttp.cli;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OptionsParserTest {

    @Test(expected = OptionsException.class)
    public void cannotParseEmptyArgs() throws OptionsException {
        OptionsParser.parse(new String[]{});
    }

    @Test
    public void canParseHelpOptionWithoutArg() throws OptionsException {
        String[][] examples = new String[][]{
                {"-h"}, {"help"}, {"help", "--help"}, {"-h", "--abc", "--defefe", "-h"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            HelpOptions result = options.run(c -> null, s -> null, h -> h);
            assertNotNull(result);
            assertEquals("Example: " + exampleText, HelpOptions.GENERAL, result);
        }
    }

    @Test
    public void canParseHelpOptionWithArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"help", "send"});
        HelpOptions result = options.run(c -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SEND, result);

        options = OptionsParser.parse(new String[]{"help", "serve"});
        result = options.run(c -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SERVE, result);

        options = OptionsParser.parse(new String[]{"help", "whatever"});
        result = options.run(c -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.GENERAL, result);

        options = OptionsParser.parse(new String[]{"help", "a", "b", "c"});
        result = options.run(c -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.GENERAL, result);

        options = OptionsParser.parse(new String[]{"-h", "send"});
        result = options.run(c -> null, s -> null, h -> h);
        assertNotNull(result);
        assertEquals(HelpOptions.SEND, result);
    }

    @Test
    public void onlyOneOptionCallbackRuns() throws OptionsException {
        final AtomicInteger sendCount = new AtomicInteger(0);
        final AtomicInteger serveCount = new AtomicInteger(0);
        final AtomicInteger showHelpCount = new AtomicInteger(0);

        Consumer<Options> run = (result) ->
                result.run(c -> sendCount.incrementAndGet(),
                        s -> serveCount.incrementAndGet(),
                        h -> showHelpCount.incrementAndGet());

        Options result = OptionsParser.parse(new String[]{"send"});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(0, serveCount.get());
        assertEquals(0, showHelpCount.get());

        result = OptionsParser.parse(new String[]{"serve", "."});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(1, serveCount.get());
        assertEquals(0, showHelpCount.get());

        result = OptionsParser.parse(new String[]{"help"});
        run.accept(result);

        assertEquals(1, sendCount.get());
        assertEquals(1, serveCount.get());
        assertEquals(1, showHelpCount.get());
    }

    @Test
    public void onlyOneClientOptionCallbackRuns() throws OptionsException {
        final AtomicInteger serveCount = new AtomicInteger(0);
        final AtomicInteger showHelpCount = new AtomicInteger(0);
        final AtomicInteger sysinCount = new AtomicInteger(0);
        final AtomicInteger textCount = new AtomicInteger(0);
        final AtomicInteger fileCount = new AtomicInteger(0);

        Consumer<Options> run = (result) ->
                result.run(c -> c.run(o -> sysinCount.incrementAndGet(),
                        (f, o) -> fileCount.incrementAndGet(),
                        (t, o) -> textCount.incrementAndGet()),
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
                    s -> "server", h -> "help");
            assertEquals("Example: " + exampleText, expectedFileName, result);
        }
    }

    @Test
    public void cannotParseSendFileOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-f"}, {"send", "--file"},
                {"send", "-p", "-f"}, {"send", "--body-file", "my-file", "--file"}
        };

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
                    s -> "server", h -> "help");
            assertEquals("Example: " + exampleText,
                    expectedRequestText, result);
        }
    }

    @Test
    public void cannotParseSendRequestTextOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-t"}, {"send", "--text"},
                {"send", "-b", "b", "-t"}, {"send", "--body-file", "b", "-p", "--text"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + exampleText);
            } catch (OptionsException e) {
                assertEquals("Example: " + exampleText,
                        "Missing argument for " +
                                example[example.length - 1] +
                                " flag", e.getMessage());
            }
        }
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
                    s -> null, h -> null);

            assertNotNull("Example: " + exampleText, result);

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
                    s -> null, h -> null);

            assertNotNull("Example: " + exampleText, result);

            File expectedBodyFile = new File(example[2]);
            File actualBodyFile = result.run(f -> f, c -> null);
            assertNotNull(actualBodyFile);
            assertEquals("Example: " + exampleText, expectedBodyFile, actualBodyFile);
        }
    }

    @Test
    public void cannotParseSendBodyFileOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-g"}, {"send", "--body-file"},
                {"send", "-p", "-b"}, {"send", "--file", "my-file", "--body-file"}
        };

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

    @Test
    public void cannotParseSendBodyTextOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"send", "-b"}, {"send", "--body-text"},
                {"send", "-p", "-b"}, {"send", "--file", "my-file", "--body-text"}
        };

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

    @Test
    public void cannotUseSendBodyTextOptionTwice() {
        String[][] examples = new String[][]{
                {"send", "-b", ".", "-b", "other"},
                {"send", "--body-text", ".", "--body-text", "other"},
                {"send", "-b", ".", "--body-text", "other"},
                {"send", "--print-body-only", "--body-text", ".", "-b", "other"}
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
                {"send", "--print-body-only", "--body-file", ".", "-g", "other"}
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

        RequestRunOptions result = options.run(
                c -> c.run(o -> o,
                        (f, o) -> null,
                        (t, o) -> null),
                s -> null, h -> null);
        assertNotNull(result);
        assertFalse(result.getRequestBody().isPresent());
        assertFalse(result.printBodyOnly);
    }

    @Test
    public void cannotUseSendFileOptionTwice() {
        String[][] examples = new String[][]{
                {"send", "-f", ".", "-f", "other"},
                {"send", "--file", ".", "--file", "other"},
                {"send", "-f", ".", "--file", "other"},
                {"send", "--print-body-only", "--file", ".", "-f", "other"}
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
                    "send", "--file", "file", "-p", "--text", "text.req"
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
            final RequestRunOptions options;

            Res(File requestFile, String requestText, RequestRunOptions options) {
                this.requestFile = requestFile;
                this.requestText = requestText;
                this.options = options;
            }
        }

        Options options = OptionsParser.parse(new String[]{"send", "-t", "REQ", "-p", "-g", "BODY"});
        ClientOptions clientOptions = options.run(c -> c, s -> null, h -> null);
        assertNotNull("Parsed client options", clientOptions);

        Res result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        RequestBody requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("BODY"), requestBody.run(f -> f, t -> null));
        assertTrue(result.options.printBodyOnly);
        assertEquals("REQ", result.requestText);
        assertNull(result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "-f", "REQ", "-p", "-g", "b.js"});
        clientOptions = options.run(c -> c, s -> null, h -> null);
        assertNotNull("Parsed client options", clientOptions);

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("b.js"), requestBody.run(f -> f, t -> null));
        assertTrue(result.options.printBodyOnly);
        assertNull(result.requestText);
        assertEquals(new File("REQ"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--body-file", "body.js", "--file", "file.req"});
        clientOptions = options.run(c -> c, s -> null, h -> null);
        assertNotNull("Parsed client options", clientOptions);

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("body.js"), requestBody.run(f -> f, t -> null));
        assertFalse(result.options.printBodyOnly);
        assertNull(result.requestText);
        assertEquals(new File("file.req"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--body-text", "Hello", "--file", "file2.req"});
        clientOptions = options.run(c -> c, s -> null, h -> null);
        assertNotNull("Parsed client options", clientOptions);

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals("Hello", requestBody.run(f -> null, t -> t));
        assertFalse(result.options.printBodyOnly);
        assertNull(result.requestText);
        assertEquals(new File("file2.req"), result.requestFile);

        options = OptionsParser.parse(new String[]{"send", "--body-file", "my.body", "--file", "file2.req"});
        clientOptions = options.run(c -> c, s -> null, h -> null);
        assertNotNull("Parsed client options", clientOptions);

        result = clientOptions.run(
                o -> new Res(null, null, o),
                (f, o) -> new Res(f, null, o),
                (t, o) -> new Res(null, t, o));

        requestBody = result.options.getRequestBody().orElseThrow(() ->
                new AssertionError("Request body is not present"));
        assertEquals(new File("my.body"), requestBody.run(f -> f, t -> null));
        assertFalse(result.options.printBodyOnly);
        assertNull(result.requestText);
        assertEquals(new File("file2.req"), result.requestFile);
    }

    @Test
    public void canParseServerOptionWithoutOptions() throws OptionsException {
        String[][] examples = new String[][]{
                {"serve", "."}, {"serve", "my/path"}
        };

        for (String[] example : examples) {
            String exampleText = Arrays.toString(example);

            Options options = OptionsParser.parse(example);

            ServerOptions result = options.run(c -> null, s -> s, h -> null);

            assertNotNull("Parsed server options. Example: " + exampleText, result);
            assertFalse("Example: " + exampleText, result.logRequests);
            assertEquals("Example: " + exampleText,
                    ServerOptions.DEFAULT_SERVER_PORT, result.port);

            File expectedDir = new File(example[1]);
            assertEquals("Example: " + exampleText,
                    expectedDir, result.dir);
        }
    }

    @Test
    public void canParseServerAbsolutePath() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"serve", "/temp/dir"});
        ServerOptions result = options.run(c -> null, s -> s, h -> null);

        assertNotNull("Parsed server options", result);
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

            ServerOptions result = options.run(c -> null, s -> s, h -> null);

            assertNotNull("Parsed server options. Example: " + exampleText, result);
            assertFalse("Example: " + exampleText, result.logRequests);

            File expectedDir = new File(example[1]);
            assertEquals("Example: " + exampleText, expectedDir, result.dir);

            int expectedPort = Integer.parseInt(example[3]);
            assertEquals("Example: " + exampleText, expectedPort, result.port);
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

            ServerOptions result = options.run(c -> null, s -> s, h -> null);

            assertNotNull("Parsed server options. Example: " + exampleText, result);
            assertFalse("Example: " + exampleText, result.logRequests);

            File expectedMediaFile = new File(example[3]);
            assertEquals("Example: " + exampleText, expectedMediaFile, result.getMediaTypesFile().orElseThrow(() ->
                    new AssertionError("Media Types file is not present")));
        }
    }

    @Test
    public void canParseServerOptionWithAllArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{"serve", "something", "-p", "33", "-l"});
        ServerOptions result = options.run(c -> null, s -> s, h -> null);

        assertNotNull("Parsed server options", result);
        assertTrue(result.logRequests);
        assertEquals(new File("something"), result.dir);
        assertEquals(33, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());

        options = OptionsParser.parse(new String[]{"serve", "my/files", "-p", "44", "--log-requests"});
        result = options.run(c -> null, s -> s, h -> null);

        assertNotNull("Parsed server options", result);
        assertTrue(result.logRequests);
        assertEquals(new File("my/files"), result.dir);
        assertEquals(44, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());

        options = OptionsParser.parse(new String[]{"serve", "/abs/path", "--log-requests", "-p", "55"});
        result = options.run(c -> null, s -> s, h -> null);

        assertNotNull("Parsed server options", result);
        assertTrue(result.logRequests);
        assertEquals(new File("/abs/path"), result.dir);
        assertEquals(55, result.port);
        assertFalse(result.getMediaTypesFile().isPresent());

        options = OptionsParser.parse(new String[]{"serve", "/temp", "-l", "--port", "66", "--media-types", "media.properties"});
        result = options.run(c -> null, s -> s, h -> null);

        assertNotNull("Parsed server options", result);
        assertTrue(result.logRequests);
        assertEquals(new File("/temp"), result.dir);
        assertEquals(66, result.port);
        assertEquals(new File("media.properties"), result.getMediaTypesFile().orElseThrow(() ->
                new AssertionError("Media Types file is not present")));
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
        assertExampleFails.accept(new String[]{"send", "-p", "-f", "file", "-z", "-p"}, "-z");
    }

}
