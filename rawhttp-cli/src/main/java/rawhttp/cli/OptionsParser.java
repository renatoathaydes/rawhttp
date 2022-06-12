package rawhttp.cli;

import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

enum HelpOptions {
    GENERAL, SERVE, SEND, RUN
}

final class ServerOptions {
    static final int DEFAULT_SERVER_PORT = 8080;

    final File dir;
    final int port;
    final boolean logRequests;
    final String rootPath;
    final URL keystore;
    final String keystorePass;
    private final File mediaTypesFile;

    ServerOptions(File dir, int port, boolean logRequests,
                  File mediaTypesFile, String rootPath,
                  URL keystore, String keystorePass) {
        this.dir = dir;
        this.port = port;
        this.logRequests = logRequests;
        this.mediaTypesFile = mediaTypesFile;
        this.rootPath = rootPath;
        this.keystore = keystore;
        this.keystorePass = keystorePass;
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

final class SendRequestOptions {
    private final RequestBody requestBody;
    final PrintResponseMode printResponseMode;
    final boolean logRequest;
    final boolean ignoreTlsCertificate;

    SendRequestOptions(RequestBody requestBody, PrintResponseMode printResponseMode,
                       boolean logRequest, boolean ignoreTlsCertificate) {
        this.requestBody = requestBody;
        this.printResponseMode = printResponseMode == null ? PrintResponseMode.RESPONSE : printResponseMode;
        this.logRequest = logRequest;
        this.ignoreTlsCertificate = ignoreTlsCertificate;
    }

    public Optional<RequestBody> getRequestBody() {
        return Optional.ofNullable(requestBody);
    }
}

final class ClientOptions {
    private final File requestFile;
    private final String requestText;
    private final SendRequestOptions sendRequestOptions;

    static ClientOptions readFromStdin(SendRequestOptions sendRequestOptions) {
        return new ClientOptions(null, null, sendRequestOptions);
    }

    static ClientOptions withFile(File requestFile, SendRequestOptions sendRequestOptions) {
        return new ClientOptions(requestFile, null, sendRequestOptions);
    }

    static ClientOptions withRequestText(String requestText, SendRequestOptions sendRequestOptions) {
        return new ClientOptions(null, requestText, sendRequestOptions);
    }

    private ClientOptions(File requestFile,
                          String requestText,
                          SendRequestOptions sendRequestOptions) {
        this.requestFile = requestFile;
        this.requestText = requestText;
        this.sendRequestOptions = sendRequestOptions;
    }

    <T> T run(Function<SendRequestOptions, T> runFromSysin,
              BiFunction<File, SendRequestOptions, T> runFile,
              BiFunction<String, SendRequestOptions, T> runText) {
        if (requestFile != null) {
            return runFile.apply(requestFile, sendRequestOptions);
        }
        if (requestText != null) {
            return runText.apply(requestText, sendRequestOptions);
        }
        return runFromSysin.apply(sendRequestOptions);
    }

}

final class HttpFileOptions {
    final File httpFile;
    @Nullable
    final File cookieJar;
    @Nullable
    final String envName;
    final PrintResponseMode printResponseMode;
    final boolean logRequest;
    final boolean ignoreTlsCert;

    public HttpFileOptions(File httpFile, @Nullable File cookieJar,
                           @Nullable String envName,
                           PrintResponseMode printResponseMode,
                           boolean logRequest, boolean ignoreTlsCert) {
        this.httpFile = httpFile;
        this.cookieJar = cookieJar;
        this.envName = envName;
        this.printResponseMode = printResponseMode;
        this.logRequest = logRequest;
        this.ignoreTlsCert = ignoreTlsCert;
    }
}

final class OptionsException extends Exception {
    OptionsException(String message) {
        super(message);
    }
}

final class Options {
    private final ClientOptions clientOptions;
    private final HttpFileOptions httpFileOptions;
    private final ServerOptions serverOptions;
    private final HelpOptions helpOptions;

    static Options justShowHelp(HelpOptions helpOptions) {
        return new Options(null, null, null, helpOptions);
    }

    static Options withClientOptions(ClientOptions clientOptions) {
        return new Options(clientOptions, null, null, null);
    }

    static Options withHttpFileOptions(HttpFileOptions httpFileOptions) {
        return new Options(null, httpFileOptions, null, null);
    }

    static Options withServerOptions(ServerOptions serverOptions) {
        return new Options(null, null, serverOptions, null);
    }

    private Options(ClientOptions clientOptions,
                    HttpFileOptions httpFileOptions,
                    ServerOptions serverOptions,
                    HelpOptions helpOptions) {
        this.clientOptions = clientOptions;
        this.httpFileOptions = httpFileOptions;
        this.serverOptions = serverOptions;
        this.helpOptions = helpOptions;
    }

    <T> T run(Function<ClientOptions, T> runClient,
              Function<HttpFileOptions, T> runHttpFile,
              Function<ServerOptions, T> runServer,
              Function<HelpOptions, T> showHelp) {
        if (clientOptions != null) {
            return runClient.apply(clientOptions);
        }
        if (httpFileOptions != null) {
            return runHttpFile.apply(httpFileOptions);
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
        return "Options{" + run(c -> c, h -> h, s -> s, h -> h) + "}";
    }
}

final class OptionsParser {

    private static final String AVAILABLE_COMMANDS_MSG =
            "Available commands are 'send', 'run', 'serve' and 'help'.\n" +
                    "Use the 'help' command to show more information.";

    static Options parse(String[] args) throws OptionsException {
        if (args.length == 0) {
            throw new OptionsException("No sub-command provided. " + AVAILABLE_COMMANDS_MSG);
        }
        String subCommand = args[0];
        switch (subCommand) {
            case "send":
                return parseSendCommand(args);
            case "run":
                return parseRunCommand(args);
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
        PrintResponseMode printResponseMode = null;
        boolean logRequest = false;
        boolean ignoreTlsCert = false;

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
                case "--print-response-mode":
                    if (printResponseMode != null) {
                        throw new OptionsException("The --print-response-mode option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        printResponseMode = PrintResponseMode.parseOption(arg, args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " option");
                    }
                    break;
                case "-l":
                case "--log-request":
                    logRequest = true;
                    break;
                case "-i":
                case "--ignore-tls-cert":
                    ignoreTlsCert = true;
                    break;
                default:
                    throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        SendRequestOptions options = new SendRequestOptions(requestBody, printResponseMode,
                logRequest, ignoreTlsCert);

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

    private static Options parseRunCommand(String[] args) throws OptionsException {
        File httpFile = null;
        @Nullable File cookieJar = null;
        @Nullable String envName = null;
        PrintResponseMode printResponseMode = null;
        boolean logRequest = false;
        boolean ignoreTlsCert = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                if (httpFile == null) {
                    httpFile = new File(arg);
                } else {
                    throw new OptionsException("Can only specify one http-file to run");
                }
                continue;
            }
            switch (arg) {
                case "-e":
                case "--environment":
                    if (envName != null) {
                        throw new OptionsException("the --environment option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        envName = args[i + 1];
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-c":
                case "--cookiejar":
                    if (cookieJar != null) {
                        throw new OptionsException("the --cookiejar option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        cookieJar = new File(args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-p":
                case "--print-response-mode":
                    if (printResponseMode != null) {
                        throw new OptionsException("The --print-response-mode option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        printResponseMode = PrintResponseMode.parseOption(arg, args[i + 1]);
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " option");
                    }
                    break;
                case "-l":
                case "--log-request":
                    logRequest = true;
                    break;
                case "-i":
                case "--ignore-tls-cert":
                    ignoreTlsCert = true;
                    break;
                default:
                    throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        if (httpFile == null) {
            throw new OptionsException("No http-file specified to run.");
        }

        if (printResponseMode == null) {
            printResponseMode = PrintResponseMode.RESPONSE;
        }

        return Options.withHttpFileOptions(new HttpFileOptions(httpFile, cookieJar, envName, printResponseMode,
                logRequest, ignoreTlsCert));
    }

    private static Options parseServeCommand(String[] args) throws OptionsException {
        File dir = null;
        boolean logRequests = false;
        File mediaTypesFile = null;
        Integer port = null;
        String rootPath = "", keystore = null, keystorePass = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                if (dir == null) {
                    dir = new File(arg);
                } else {
                    throw new OptionsException("Can only specify one directory to serve");
                }
                continue;
            }
            switch (arg) {
                case "-l":
                case "--log-requests":
                    logRequests = true;
                    break;
                case "-m":
                case "--media-types":
                    if (mediaTypesFile != null) {
                        throw new OptionsException("The --media-types option can only be used once");
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
                        throw new OptionsException("The --port option can only be used once");
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
                case "-k":
                case "--keystore":
                    if (keystore != null) {
                        throw new OptionsException("The --keystore option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        keystore = args[i + 1];
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-w":
                case "--keystore-password":
                    if (keystorePass != null) {
                        throw new OptionsException("The --keystore-password option can only be used once");
                    }
                    if (i + 1 < args.length) {
                        keystorePass = args[i + 1];
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                case "-r":
                case "--root-path":
                    if (i + 1 < args.length) {
                        rootPath = args[i + 1];
                        i++;
                    } else {
                        throw new OptionsException("Missing argument for " + arg + " flag");
                    }
                    break;
                default:
                    throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        if (dir == null) {
            throw new OptionsException("No directory specified to serve from.");
        }

        URL keystoreURL = null;
        if (keystore != null) {
            try {
                keystoreURL = keystore.contains("://")
                        ? new URL(keystore)
                        : new File(keystore).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new OptionsException("Invalid URL: " + keystore);
            }
        }

        return Options.withServerOptions(
                new ServerOptions(dir, port == null
                        ? ServerOptions.DEFAULT_SERVER_PORT
                        : port,
                        logRequests,
                        mediaTypesFile,
                        rootPath,
                        keystoreURL,
                        keystorePass));
    }

}
