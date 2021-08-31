package rawhttp.samples;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.RedirectingRawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedirectSample {
    static final int PORT = 8099;

    static final RawHttp http = new RawHttp();

    static TcpRawHttpServer server;

    @BeforeAll
    static void startServer() {
        server = new TcpRawHttpServer(PORT);
        server.start((request) -> {
            if ("/first-request".equals(request.getUri().getPath())) {
                return Optional.of(http.parseResponse("HTTP/1.1 302 REDIRECT\r\n" +
                        "Content-Length: 0\r\n" +
                        "Location: /final-request"));
            }
            if ("/final-request".equals(request.getUri().getPath())) {
                return Optional.of(http.parseResponse("HTTP/1.1 200 OK\r\nContent-Length: 0"));
            }
            return Optional.empty();
        });
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    public void canRedirect() throws IOException {
        TcpRawHttpClient rawClient = new TcpRawHttpClient();
        RawHttpClient<Void> redirectingRawHttpClient = new RedirectingRawHttpClient<>(rawClient);

        try {
            RawHttpRequest request = http.parseRequest("GET http://localhost:" + PORT + "/first-request");
            RawHttpResponse<?> response = redirectingRawHttpClient.send(request).eagerly();

            assertEquals(200, response.getStatusCode());
        } finally {
            rawClient.close();
        }
    }
}
