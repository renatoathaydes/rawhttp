package com.athaydes.rawhttp.cli;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OptionsParserTest {

    @Test
    public void canParseEmptyArgs() throws OptionsException {
        Options options = OptionsParser.parse(new String[]{});

        assertFalse(options.requestFile.isPresent());
        assertFalse(options.serverOptions.isPresent());
        assertFalse(options.showHelp);
    }

    @Test
    public void canParseHelpOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"-h"}, {"--help"}, {"--help", "--help"}, {"-h", "--help", "--help", "-h"}
        };

        for (String[] example : examples) {
            Options options = OptionsParser.parse(example);

            assertFalse(options.requestFile.isPresent());
            assertFalse(options.serverOptions.isPresent());
            assertTrue(options.showHelp);
        }
    }

    @Test
    public void canParseFileOption() throws OptionsException {
        String[][] examples = new String[][]{
                {"-f", "hello"}, {"--file", "hi"}
        };

        for (String[] example : examples) {
            String expectedFileName = example[1];
            Options options = OptionsParser.parse(example);

            assertTrue(options.requestFile.isPresent());
            assertEquals(expectedFileName, options.requestFile.get().getName());
            assertFalse(options.serverOptions.isPresent());
            assertFalse(options.showHelp);
        }
    }

    @Test
    public void cannotParseFileOptionMissingMandatoryArg() {
        String[][] examples = new String[][]{
                {"-f"}, {"--file"}, {"-h", "-f"}, {"--help", "-s", ".", "--file"}
        };

        for (String[] example : examples) {
            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + Arrays.toString(example));
            } catch (OptionsException e) {
                assertEquals("--file -f: option requires a file argument", e.getMessage());
            }
        }
    }

    @Test
    public void cannotUseFileOptionTwice() {
        String[][] examples = new String[][]{
                {"-f", ".", "-f", "other"},
                {"--file", ".", "--file", "other"},
                {"-f", ".", "--file", "other"},
                {"--server", "path", "--file", ".", "-f", "other"}
        };

        for (String[] example : examples) {
            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + Arrays.toString(example));
            } catch (OptionsException e) {
                assertEquals("--file -f: option cannot be used more than once", e.getMessage());
            }
        }
    }

    @Test
    public void canParseServerOptionWithoutArgs() throws OptionsException {
        String[][] examples = new String[][]{
                {"-s"}, {"--server"}
        };

        File expectedDir = new File(".").getAbsoluteFile();

        for (String[] example : examples) {
            Options options = OptionsParser.parse(example);

            assertFalse(options.requestFile.isPresent());
            assertFalse(options.showHelp);

            assertTrue(options.serverOptions.isPresent());

            ServerOptions serverOptions = options.serverOptions.get();

            assertEquals(expectedDir, serverOptions.dir);
            assertEquals(ServerOptions.DEFAULT_SERVER_PORT, serverOptions.port);
        }
    }

    @Test
    public void canParseServerOptionWithoutPort() throws OptionsException {
        String[][] examples = new String[][]{
                {"-s", "boo"}, {"--server", "another/path"}
        };

        for (String[] example : examples) {
            File expectedDir = new File(example[1]);
            Options options = OptionsParser.parse(example);

            assertFalse(options.requestFile.isPresent());
            assertFalse(options.showHelp);

            assertTrue(options.serverOptions.isPresent());

            ServerOptions serverOptions = options.serverOptions.get();

            assertEquals(expectedDir, serverOptions.dir);
            assertEquals(ServerOptions.DEFAULT_SERVER_PORT, serverOptions.port);
        }
    }

    @Test
    public void canParseServerOptionWithAllArgs() throws OptionsException {
        String[][] examples = new String[][]{
                {"-s", "something", "33"}, {"--server", "other/inner/path", "8092", "-l"},
                {"-s", "hello", "4040", "--log-requests"}
        };

        for (String[] example : examples) {
            File expectedDir = new File(example[1]);
            int expectedPort = Integer.parseInt(example[2]);
            boolean expectedLogRequests = example.length > 3;

            Options options = OptionsParser.parse(example);

            assertFalse(options.requestFile.isPresent());
            assertFalse(options.showHelp);

            assertTrue(options.serverOptions.isPresent());

            ServerOptions serverOptions = options.serverOptions.get();

            assertEquals(expectedDir, serverOptions.dir);
            assertEquals(expectedPort, serverOptions.port);
            assertEquals(expectedLogRequests, serverOptions.logRequests);
        }
    }

    @Test
    public void logRequestsOptionCanAppearAnywhereWithServer() throws OptionsException {
        String[][] examples = new String[][]{
                {"--log-requests", "-s", "something", "33"},
                {"--server", "other/inner/path", "-l"},
                {"-s", "path", "--log-requests"},
                {"-s", "path", "-h", "--log-requests", "-l"}
        };

        for (String[] example : examples) {
            Options options = OptionsParser.parse(example);

            assertTrue(options.serverOptions.isPresent());

            ServerOptions serverOptions = options.serverOptions.get();
            assertTrue(serverOptions.logRequests);
        }
    }

    @Test
    public void cannotUseServerOptionTwice() {
        String[][] examples = new String[][]{
                {"-s", ".", "-s", "other"},
                {"--server", ".", "--server", "other"},
                {"-s", ".", "--server", "other"},
                {"--file", "path", "--server", ".", "-s", "other"}
        };

        for (String[] example : examples) {
            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + Arrays.toString(example));
            } catch (OptionsException e) {
                assertEquals("--server -s: option cannot be used more than once", e.getMessage());
            }
        }
    }

    @Test
    public void doesNotAcceptUnrecognizedOptions() {
        String[][] examples = new String[][]{
                {"-s", ".", "other"},
                {"--some"},
                {"-s", ".", "--file", "other", "--something"},
                {"--file", "path", "--server", ".", "-h", "other"},
                {"--boo", "path", "--server", ".", "--hi", "other"}
        };

        for (String[] example : examples) {
            try {
                OptionsParser.parse(example);
                fail("Did not fail to parse example: " + Arrays.toString(example));
            } catch (OptionsException e) {
                String invalidOption = "?";
                for (String ex : example) {
                    if (!ex.isEmpty()) {
                        invalidOption = ex;
                        break;
                    }
                }
                assertEquals("Unrecognized option: " + invalidOption, e.getMessage());
            }
        }
    }

}
