package rawhttp.cli;

import com.athaydes.rawhttp.cli.Versions;
import com.athaydes.rawhttp.reqinedit.ReqInEditParser;
import com.athaydes.rawhttp.reqinedit.ReqInEditUnit;
import rawhttp.cli.client.RawHttpCliClient;
import rawhttp.cli.client.RawHttpCliTestsReporter;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.body.FileBody;
import rawhttp.core.body.HttpMessageBody;
import rawhttp.core.body.StringBody;
import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.core.server.RawHttpServer;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Properties;

public class Main {

    private static class CliError {
        final ErrorCode code;
        final String message;

        CliError(ErrorCode code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private enum ErrorCode {
        BAD_USAGE, // 1
        INVALID_HTTP_REQUEST, // 2
        UNEXPECTED_ERROR, // 3
        IO_EXCEPTION // 4
    }

    private static final RawHttp HTTP = new RawHttp(RawHttpOptions.newBuilder()
            .allowIllegalStartLineCharacters()
            .build());

    public static void main(String[] args) {
        CliError error;
        try {
            error = OptionsParser.parse(args).run(
                    clientOptions -> clientOptions.run(
                            Main::sendRequestFromSysIn,
                            Main::sendRequestFromFile,
                            Main::sendRequestFromText),
                    Main::runHttpFile,
                    Main::serve,
                    Main::showUsage);
        } catch (OptionsException e) {
            error = new CliError(ErrorCode.BAD_USAGE, e.getMessage());
        } catch (InvalidHttpRequest e) {
            error = new CliError(ErrorCode.INVALID_HTTP_REQUEST, e.toString());
        } catch (Exception e) {
            error = new CliError(ErrorCode.UNEXPECTED_ERROR, e.toString());
        }
        if (error != null) {
            System.err.println(error.message);
            System.exit(1 + error.code.ordinal());
        }
    }

    private static CliError showUsage(HelpOptions options) {
        System.out.println("=============== RawHTTP CLI ===============");
        System.out.println(" https://github.com/renatoathaydes/rawhttp");
        System.out.println(" rawhttp-cli  version: " + Versions.CLI_VERSION);
        System.out.println(" rawhttp-core version: " + Versions.CORE_VERSION);
        System.out.println("===========================================");

        switch (options) {
            case GENERAL:
                System.out.println("\n" +
                        "RawHTTP CLI is a utility to send and receive HTTP messages.\n" +
                        "The following sub-commands are available:\n" +
                        "\n" +
                        "  send    - sends a HTTP request\n" +
                        "  run     - runs a HTTP file in the IntelliJ format.\n" +
                        "  serve   - serves the contents of a local directory via HTTP.\n" +
                        "  help    - shows this message or help for a specific sub-command.\n" +
                        "\n" +
                        "Send Command Usage:\n" +
                        "  rawhttp send [options]\n" +
                        "Run Command Usage:\n" +
                        "  rawhttp run <http-file> [options]\n" +
                        "\n" +
                        "Serve Command Usage:\n" +
                        "  rawhttp serve <directory> [options]\n" +
                        "\n" +
                        "Help Command Usage:\n" +
                        "  rawhttp help send|serve\n" +
                        "\n" +
                        "Detailed documentation at https://renatoathaydes.github.io/rawhttp\n");
                break;
            case SEND:
                System.out.println("\n" +
                        "Send sub-command Help.\n" +
                        "\n" +
                        "The 'send' sub-command is used to send out a single HTTP request.\n" +
                        "\n" +
                        "Usage:\n" +
                        "  rawhttp send [options]\n" +
                        "\n" +
                        "Options:\n" +
                        "  * -f --file <file>\n" +
                        "      read request from a file\n" +
                        "  * -t --text <request-text>\n" +
                        "      read request as text\n" +
                        "  * -p --print-response-mode <option>\n" +
                        "      option is one of: response|all|body|status|stats\n" +
                        "        - response: (default) print the full response\n" +
                        "        - all: print the full response and statistics about the request\n" +
                        "        - body: print the response body\n" +
                        "        - status: print the response status-line\n" +
                        "        - stats: print statistics about the request\n" +
                        "  * -l --log-request\n" +
                        "      log the request\n" +
                        "  * -b --body-text <text>\n" +
                        "      replace message body with the text\n" +
                        "  * -g --body-file <file>\n" +
                        "      replace message body with the contents of the file\n" +
                        "\n" +
                        "Statistics include the following information:\n" +
                        "  Connect time: the time it took to connect to the server (includes only the Socket::connect call).\n" +
                        "  First received byte time (FRBT): the time between the first byte of the request being sent, and the first byte of the response being received.\n" +
                        "  Total response time (TRT): time to receive the first byte, plus the time to download the full response.\n" +
                        "  Bytes received: the number of bytes received from the server.\n" +
                        "  Throughput: the total number of bytes received, divided by (TRT - FRBT) in seconds.\n" +
                        "\n" +
                        "If no -f or -t options are given, a HTTP request is read from stdin.\n");
                break;
            case RUN:
                System.out.println("\n" +
                        "Run sub-command Help.\n" +
                        "\n" +
                        "The 'run' sub-command executes a HTTP file as defined by Jetbrains at\n" +
                        "\n" +
                        "https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html\n" +
                        "\n" +
                        "Usage:\n" +
                        "  rawhttp run <http-file> [options]\n" +
                        "\n" +
                        "Options:\n" +
                        "  * -e --environment <name>\n" +
                        "    the name of the environment to use\n" +
                        "  * -p --print-response-mode\n" +
                        "  *   one of: response|all|body|status|stats\n" +
                        "        - response: (default) print the full responses\n" +
                        "        - all: print the full response and statistics about each request\n" +
                        "        - body: print the response bodies\n" +
                        "        - status: print the response status-lines\n" +
                        "        - stats: print statistics about each request\n" +
                        "  * -l --log-request\n" +
                        "      log the request\n"
                );
            case SERVE:
                System.out.println("\n" +
                        "Serve sub-command Help.\n" +
                        "\n" +
                        "The 'serve' sub-command is used to serve a local directory via HTTP.\n" +
                        "\n" +
                        "Request paths may match a file name with or without its extension.\n" +
                        "If more than one file exists with the same name but different extensions,\n" +
                        "the server attempts to use the request Accept header to disambiguate.\n" +
                        "\n" +
                        "Usage:\n" +
                        "  rawhttp serve <dir> [options]\n" +
                        "\n" +
                        "Options:\n" +
                        "  * -l --log-requests\n" +
                        "      log requests received by the server\n" +
                        "  * -m --media-types <file>\n" +
                        "      use custom Media-Type mappings\n" +
                        "  * -p --port <port-number>\n" +
                        "      the port to listen on\n" +
                        "  * -r --root-path <path>\n" +
                        "      the path to use as the root path (not incl. in file path, only URL)\n");
                break;
            default:
                return new CliError(ErrorCode.UNEXPECTED_ERROR, "Help option is not covered: " + options);
        }
        return null;
    }

    private static CliError sendRequestFromText(String request, SendRequestOptions options) {
        return sendRequest(HTTP.parseRequest(request), options);
    }

    private static CliError sendRequestFromSysIn(SendRequestOptions options) {
        try {
            return sendRequest(HTTP.parseRequest(System.in), options);
        } catch (IOException e) {
            return new CliError(ErrorCode.IO_EXCEPTION, e.toString());
        }
    }

    private static CliError sendRequestFromFile(File file, SendRequestOptions options) {
        try (InputStream fileStream = Files.newInputStream(file.toPath())) {
            return sendRequest(HTTP.parseRequest(fileStream), options);
        } catch (IOException e) {
            return new CliError(ErrorCode.IO_EXCEPTION, e.toString());
        }
    }

    private static CliError sendRequest(RawHttpRequest request, SendRequestOptions options) {
        if (options.getRequestBody().isPresent()) {
            HttpMessageBody requestBody = options.getRequestBody().get().run(
                    FileBody::new,
                    StringBody::new);

            if (requestBody instanceof FileBody) {
                File file = ((FileBody) requestBody).getFile();
                if (file.isDirectory()) {
                    return new CliError(ErrorCode.UNEXPECTED_ERROR, "Request body file cannot be a directory: " + file);
                }
                if (!file.isFile()) {
                    return new CliError(ErrorCode.UNEXPECTED_ERROR, "Request body file does not exist: " + file);
                }
            }
            request = request.withBody(requestBody);
        }

        try (RawHttpCliClient client = new RawHttpCliClient(options.logRequest, options.printResponseMode)) {
            client.send(request);
        } catch (IOException e) {
            return new CliError(ErrorCode.IO_EXCEPTION, e.toString());
        }
        return null;
    }

    private static CliError runHttpFile(HttpFileOptions httpFileOptions) {
        ReqInEditParser parser = new ReqInEditParser();

        try (RawHttpCliClient httpClient = new RawHttpCliClient(
                httpFileOptions.logRequest, httpFileOptions.printResponseMode)) {
            ReqInEditUnit unit = parser.parse(httpFileOptions.httpFile, httpFileOptions.envName);
            unit.runWith(httpClient, new RawHttpCliTestsReporter());
        } catch (IOException e) {
            return new CliError(ErrorCode.IO_EXCEPTION, e.toString());
        }
        return null;
    }

    private static CliError serve(ServerOptions options) {
        if (options.dir.isFile()) {
            return new CliError(ErrorCode.BAD_USAGE, "Error: not a directory - " + options.dir);
        }

        System.out.println("Serving directory " + options.dir.getAbsolutePath() + " on port " + options.port);

        if (!options.dir.exists()) {
            System.err.println("Warning: The provided directory does not exist: " + options.dir + "\n" +
                    "The server will not serve any files until the directory exists.");
        }

        RequestLogger requestLogger = options.logRequests
                ? new AsyncSysoutRequestLogger()
                : new NoopRequestLogger();
        RawHttpServer server = new TcpRawHttpServer(new CliServerOptions(options.port, requestLogger));
        Optional<File> mediaTypesFile = options.getMediaTypesFile();
        if (mediaTypesFile.isPresent()) {
            Properties mediaTypeProperties = new Properties();
            try {
                mediaTypeProperties.load(new FileReader(mediaTypesFile.get()));
            } catch (IOException e) {
                return new CliError(ErrorCode.IO_EXCEPTION, "Could not read media-types file: " + e.getMessage());
            }
            server.start(new CliServerRouter(options.dir, options.rootPath, mediaTypeProperties));
        } else {
            server.start(new CliServerRouter(options.dir, options.rootPath));
        }
        return null;
    }

}
