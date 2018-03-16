package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.body.FileBody;
import com.athaydes.rawhttp.core.client.TcpRawHttpClient;
import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;
import com.athaydes.rawhttp.core.server.RawHttpServer;
import com.athaydes.rawhttp.core.server.Router;
import com.athaydes.rawhttp.core.server.TcpRawHttpServer;

import java.io.File;
import java.io.IOException;

public class Main {

    private enum ErrorCode {
        BAD_USAGE,
        INVALID_HTTP_REQUEST,
        UNEXPECTED_ERROR,
        IO_EXCEPTION
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            readRequestFromSysIn();
        } else try {
            Options options = OptionsParser.parse(args);
            if (options.showHelp) {
                showUsage();
            } else {
                options.requestFile.ifPresent(Main::readRequestFromFile);
                options.serverOptions.ifPresent(Main::serve);
            }
        } catch (OptionsException e) {
            error(ErrorCode.BAD_USAGE,
                    e.getMessage() + "\nFor usage, run with the --help option.");
        } catch (InvalidHttpRequest e) {
            error(ErrorCode.INVALID_HTTP_REQUEST, e.toString());
        } catch (Exception e) {
            error(ErrorCode.UNEXPECTED_ERROR, e.toString());
        }
    }

    private static void showUsage() {
        System.out.println("=============== RawHTTP CLI ===============");
        System.out.println(" https://github.com/renatoathaydes/rawhttp");
        System.out.println("===========================================");

        System.out.println("\n" +
                "RawHTTP CLI is a utility to send HTTP requests to remote servers or " +
                "serve the contents of a local directory via HTTP.\n" +
                "\n" +
                "Usage:\n" +
                "  rawhttp [option [args]] | request\n" +
                "Options:\n" +
                "  --help, -h          show this help message.\n" +
                "  --file, -f <file>   send request from file.\n" +
                "  --server, -s [<dir> [port]] serve contents of directory.\n" +
                "  --log-requests, -l  log requests received by the server (--server mode).\n" +
                "If no arguments are given, RawHTTP reads a HTTP request from sysin.");
    }

    private static void readRequestFromSysIn() {
        System.out.println("TODO read from sysin");
    }

    private static void readRequest(String request) {
        RawHttp http = new RawHttp();
        TcpRawHttpClient client = new TcpRawHttpClient();
        try {
            RawHttpResponse<Void> response = client.send(http.parseRequest(request));
            response.writeTo(System.out);
        } catch (IOException e) {
            error(ErrorCode.IO_EXCEPTION, e.toString());
        }
    }

    private static void readRequestFromFile(File file) {
        System.out.println("TODO Reading from file: " + file);
    }

    private static void serve(ServerOptions options) {
        if (!options.dir.isDirectory()) {
            error(ErrorCode.BAD_USAGE, "Error: not a directory - " + options.dir);
            return;
        }

        System.out.println("Serving directory " + options.dir.getAbsolutePath() + " on port " + options.port);
        System.out.println("Press Enter key to stop the server.");

        RawHttpServer server = new TcpRawHttpServer(options.port);
        server.start(createRouter(options.dir));

        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException e) {
            error(ErrorCode.IO_EXCEPTION, e.toString());
        }
        server.stop();
    }

    private static Router createRouter(File rootDir) {
        final RawHttp http = new RawHttp();
        return request -> {
            if (request.getMethod().equals("GET")) {
                String path = request.getStartLine().getUri().getPath();
                File resource = new File(rootDir, path);
                if (resource.isFile()) {
                    RawHttpResponse<Void> response = http.parseResponse(request.getStartLine().getHttpVersion() +
                            " 200 OK\n" +
                            "Content-Type: application/octet-stream\n" +
                            "Server: RawHTTP");
                    return response.replaceBody(new FileBody(resource));
                }
                return http.parseResponse(request.getStartLine().getHttpVersion() +
                        " 404 Not Found\n" +
                        "Content-Length: 24\n" +
                        "Content-Type: plain/text\n" +
                        "Server: RawHTTP\n\n" +
                        "Resource does not exist.");
            } else {
                return http.parseResponse(request.getStartLine().getHttpVersion() +
                        " 405 Method Not Allowed\n" +
                        "Content-Length: 19\n" +
                        "Content-Type: plain/text\n" +
                        "Server: RawHTTP\n\n" +
                        "Method not allowed.");
            }
        };
    }

    private static void error(ErrorCode code, String message) {
        System.err.println(message);
        System.exit(1 + code.ordinal());
    }

}
