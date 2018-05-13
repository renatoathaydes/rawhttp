package com.athaydes.rawhttp.cli;

import java.io.File;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

enum HelpOptions {
    GENERAL, SERVE, SEND
}

final class ServerOptions {
    static final int DEFAULT_SERVER_PORT = 8080;

    final File dir;
    final int port;
    final boolean logRequests;
    private final File mediaTypesFile;

    ServerOptions(File dir, int port, boolean logRequests,
                  File mediaTypesFile) {
        this.dir = dir;
        this.port = port;
        this.logRequests = logRequests;
        this.mediaTypesFile = mediaTypesFile;
    }

    public Optional<File> getMediaTypesFile() {
        return Optional.ofNullable(mediaTypesFile);
    }
}

final class RequestBody {
    private final File file;
    private final String text;

    public RequestBody(File file) {
        this.file = file;
        this.text = null;
    }

    public RequestBody(String text) {
        this.file = null;
        this.text = text;
    }

    <T> T run(Function<File, T> useFile, Function<String, T> useText) {
        if (file != null) {
            return useFile.apply(file);
        }
        if (text != null) {
            return useText.apply(text);
        }
        throw new IllegalStateException("Neither file nor text present");
    }
}

final class RequestRunOptions {
    private final RequestBody requestBody;
    final boolean printBodyOnly;

    RequestRunOptions(RequestBody requestBody, boolean printBodyOnly) {
        this.requestBody = requestBody;
        this.printBodyOnly = printBodyOnly;
    }

    public Optional<RequestBody> getRequestBody() {
        return Optional.ofNullable(requestBody);
    }
}

final class ClientOptions {
    private final File requestFile;
    private final String requestText;
    private final RequestRunOptions requestRunOptions;

    static ClientOptions readFromStdin(RequestRunOptions requestRunOptions) {
        return new ClientOptions(null, null, requestRunOptions);
    }

    static ClientOptions withFile(File requestFile, RequestRunOptions requestRunOptions) {
        return new ClientOptions(requestFile, null, requestRunOptions);
    }

    static ClientOptions withRequestText(String requestText, RequestRunOptions requestRunOptions) {
        return new ClientOptions(null, requestText, requestRunOptions);
    }

    private ClientOptions(File requestFile,
                          String requestText,
                          RequestRunOptions requestRunOptions) {
        this.requestFile = requestFile;
        this.requestText = requestText;
        this.requestRunOptions = requestRunOptions;
    }

    <T> T run(Function<RequestRunOptions, T> runFromSysin,
              BiFunction<File, RequestRunOptions, T> runFile,
              BiFunction<String, RequestRunOptions, T> runText) {
        if (requestFile != null) {
            return runFile.apply(requestFile, requestRunOptions);
        }
        if (requestText != null) {
            return runText.apply(requestText, requestRunOptions);
        }
        return runFromSysin.apply(requestRunOptions);
    }

}

final class OptionsException extends Exception {
    OptionsException(String message) {
        super(message);
    }
}

final class Options {
    private final ClientOptions clientOptions;
    private final ServerOptions serverOptions;
    private final HelpOptions helpOptions;

    static Options justShowHelp(HelpOptions helpOptions) {
        return new Options(null, null, helpOptions);
    }

    static Options withClientOptions(ClientOptions clientOptions) {
        return new Options(clientOptions, null, null);
    }

    static Options withServerOptions(ServerOptions serverOptions) {
        return new Options(null, serverOptions, null);
    }

    private Options(ClientOptions clientOptions,
                    ServerOptions serverOptions,
                    HelpOptions helpOptions) {
        this.clientOptions = clientOptions;
        this.serverOptions = serverOptions;
        this.helpOptions = helpOptions;
    }

    <T> T run(Function<ClientOptions, T> runClient,
              Function<ServerOptions, T> runServer,
              Function<HelpOptions, T> showHelp) {
        if (clientOptions != null) {
            return runClient.apply(clientOptions);
        }
        if (serverOptions != null) {
            return runServer.apply(serverOptions);
        }
        if (helpOptions != null) {
            return showHelp.apply(helpOptions);
        }
        throw new IllegalStateException("No option selected");
    }

    @Override
    public String toString() {
        return "Options{" + run(c -> c, s -> s, h -> h) + "}";
    }
}

final class OptionsParser {

    private static final String AVAILABLE_COMMANDS_MSG =
            "Available commands are 'send', 'serve' and 'help'.\n" +
                    "Use the 'help' command to show more information.";

    static Options parse(String[] args) throws OptionsException {
        if (args.length == 0) {
            throw new OptionsException("No sub-command provided. " + AVAILABLE_COMMANDS_MSG);
        }
        String subCommand = args[0];
        switch (subCommand) {
            case "send":
                return parseSendCommand(args);
            case "serve":
                return parseServeCommand(args);
            case "help":
            case "-h":
                HelpOptions helpOptions = HelpOptions.GENERAL;
                if (args.length > 1) try {
                    helpOptions = HelpOptions.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    // ignore invalid option
                }
                return Options.justShowHelp(helpOptions);
            default:
                throw new OptionsException("Unknown sub-command: " + subCommand + ". " +
                        AVAILABLE_COMMANDS_MSG);
        }
    }

    private static Options parseSendCommand(String[] args) throws OptionsException {
        File requestFile = null;
        String requestText = null;
        RequestBody requestBody = null;
        boolean printBodyOnly = false;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-t":
                case "--text":
                    if (requestText != null) {
                        throw new OptionsException("The --request-text option can only be used once");
                    }
                    if (requestFile != null) {
                        throw new OptionsException("Cannot use both --text and --file options together");
                    }
                    if (i + 1 < args.length) {
                        requestText = args[i + 1];
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-f":
                case "--file":
                    if (requestFile != null) {
                        throw new OptionsException("the --file option can only be used once");
                    }
                    if (requestText != null) {
                        throw new OptionsException("Cannot use both --text and --file options together");
                    }
                    if (i + 1 < args.length) {
                        requestFile = new File(args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-b":
                case "--body-text":
                    if (requestBody != null) {
                        String error = requestBody.run(
                                f -> "Cannot use both --body-text and --body-file options together",
                                t -> "The --body-text option can only be used once");
                        throw new OptionsException(error);
                    }
                    if (i + 1 < args.length) {
                        requestBody = new RequestBody(args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-g":
                case "--body-file":
                    if (requestBody != null) {
                        String error = requestBody.run(
                                f -> "The --body-file option can only be used once",
                                t -> "Cannot use both --body-text and --body-file options together");
                        throw new OptionsException(error);
                    }
                    if (i + 1 < args.length) {
                        requestBody = new RequestBody(new File(args[i + 1]));
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-p":
                case "--print-body-only":
                    printBodyOnly = true;
                    break;
                default:
                    throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        RequestRunOptions options = new RequestRunOptions(requestBody, printBodyOnly);
        ClientOptions clientOptions;
        if (requestFile != null) {
            clientOptions = ClientOptions.withFile(requestFile, options);
        } else if (requestText != null) {
            clientOptions = ClientOptions.withRequestText(requestText, options);
        } else {
            clientOptions = ClientOptions.readFromStdin(options);
        }
        return Options.withClientOptions(clientOptions);
    }

    private static Options parseServeCommand(String[] args) throws OptionsException {
        if (args.length == 1) {
            throw new OptionsException("The serve sub-command requires a directory");
        }
        File dir = new File(args[1]);
        boolean logRequests = false;
        File mediaTypesFile = null;
        Integer port = null;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-l":
                case "--log-requests":
                    logRequests = true;
                    break;
                case "-m":
                case "--media-types":
                    if (mediaTypesFile != null) {
                        throw new OptionsException("the --media-types option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        mediaTypesFile = new File(args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-p":
                case "--port":
                    if (port != null) {
                        throw new OptionsException("the --port option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[i + 1]);
                        } catch (NumberFormatException e) {
                            throw new OptionsException("Invalid port number, not a number: " + args[i + 1]);
                        }
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                default:
                    throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        return Options.withServerOptions(
                new ServerOptions(dir, port == null
                        ? ServerOptions.DEFAULT_SERVER_PORT
                        : port,
                        logRequests,
                        mediaTypesFile));
    }

}
