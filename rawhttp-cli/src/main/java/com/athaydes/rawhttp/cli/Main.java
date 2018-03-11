package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.body.FileBody;
import com.athaydes.rawhttp.core.client.TcpRawHttpClient;
import com.athaydes.rawhttp.core.server.RawHttpServer;
import com.athaydes.rawhttp.core.server.Router;
import com.athaydes.rawhttp.core.server.TcpRawHttpServer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

public class Main {

    public static final int DEFAULT_SERVER_PORT = 8080;

    public static void main(String[] args) {
        if (args.length == 0) {
            readRequestFromSysIn();
        } else {
            String arg = args[0];
            switch (arg) {
                case "-h":
                case "--help":
                    showUsage();
                    break;
                case "-s":
                case "--server":
                    switch (args.length) {
                        case 1:
                            serve(".", DEFAULT_SERVER_PORT);
                            break;
                        case 2:
                            serve(args[1], DEFAULT_SERVER_PORT);
                            break;
                        case 3:
                            Integer port = parsePort(args[2]);
                            if (port != null) {
                                serve(args[1], port);
                            }
                            break;
                        default:
                            System.err.println("Error - too many arguments! --server option takes a directory and," +
                                    " optionally, a port to use.");
                    }
                    break;
                case "-f":
                case "--file":
                    if (args.length == 2) {
                        readRequestFromFile(args[1]);
                    } else {
                        System.err.println("Error - wrong number of arguments! --file option takes a single file as argument.");
                    }
                    break;
                default:
                    readRequest(Arrays.stream(args).collect(joining(" ")));
            }
        }

    }

    private static Integer parsePort(String port) {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            System.err.println("Error - Invalid port: " + port);
        }
        return null;
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
                "  java -jar rawhttp.jar [option [args]] | request\n" +
                "Options:\n" +
                "  --help, -h          show this help message.\n" +
                "  --file, -f <file>   send request from file.\n" +
                "  --server, -s [<dir> [port]] serve contents of directory.\n" +
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
            System.err.println(e.toString());
        }
    }

    private static void readRequestFromFile(String file) {
        System.out.println("TODO Reading from file: " + file);
    }

    private static void serve(String directory, int port) {
        File rootDir = new File(directory);
        if (!rootDir.isDirectory()) {
            System.err.println("Error: not a directory - " + directory);
            return;
        }

        System.out.println("Serving directory " + rootDir.getAbsolutePath() + " on port " + port);
        System.out.println("Press Enter key to stop the server.");

        RawHttpServer server = new TcpRawHttpServer(port);
        server.start(createRouter(rootDir));

        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
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

}
