package my;

import rawhttp.core.EagerHttpRequest;
import rawhttp.core.RawHttp;
import rawhttp.core.client.TcpRawHttpClient;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;

public class ClientServerManualTest {

    static final RawHttp http = new RawHttp();

    public static void main(String[] args) throws Throwable {
        TcpRawHttpClient client = new TcpRawHttpClient(new TcpRawHttpClient.DefaultOptions() {
            @Override
            protected Socket createSocket(boolean useHttps, String host, int port) throws IOException {
                Socket s = super.createSocket(useHttps, host, port);
                s.setSoTimeout(2000);
                return s;
            }
        });

        AtomicBoolean closeConnection = new AtomicBoolean(true);
        EagerHttpRequest clientRequest = http.parseRequest("GET localhost:8088/").eagerly();
        TcpRawHttpServer server = new TcpRawHttpServer(8088);
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                String line = scanner.nextLine();
                switch (line) {
                    case "start":
                        System.out.println("Starting server");
                        server.start((request -> {
                            try {
                                return ofNullable(http.parseResponse("200 OK\n" +
                                        "Content-Length: 0\n" +
                                        (closeConnection.get() ? "Connection: close" : "")).eagerly());
                            } catch (Exception e) {
                                System.out.println("Error sending response: " + e);
                                server.stop();
                                throw new RuntimeException(e);
                            }
                        }));
                        break;
                    case "stop":
                        System.out.println("Stopping server");
                        server.stop();
                        break;
                    case "flip":
                        System.out.println("Flipping connection close responses: " + !closeConnection.get());
                        closeConnection.set(!closeConnection.get());
                        break;
                    case "ping":
                        System.out.print("Ping --> ");
                        try {
                            client.send(clientRequest);
                            System.out.println("Ping OK");
                        } catch (Exception e) {
                            System.out.println("Error: " + e);
                        }
                        break;
                    case "exit":
                        return;
                    default:
                        System.out.println("Bad command");
                }
            }
        } finally {
            System.out.println("Shutdown");
            server.stop();
            client.close();
        }
    }

}