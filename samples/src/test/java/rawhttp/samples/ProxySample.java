package rawhttp.samples;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.BodyReader;
import rawhttp.core.body.StringBody;
import rawhttp.core.client.TcpRawHttpClient;
import rawhttp.core.server.TcpRawHttpServer;
import spark.Spark;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProxySample {
    private static final int PROXY_PORT = 8988;
    private static final int TARGET_SERVER_PORT = 8989;

    private TcpRawHttpServer proxyServer;
    private TcpRawHttpClient proxyClient;

    @BeforeEach
    public void startTargetServer() throws Exception {
        Spark.port(TARGET_SERVER_PORT);
        Spark.get("/hello", "text/plain", (req, res) -> {
            System.out.println("Forwarded by " + req.headers("X-Forwarded-For"));
            return "Hello";
        });
        Spark.post("/echo", "text/plain", (req, res) -> req.body());
        Spark.put("/echo", "text/plain", (req, res) -> req.body());
        RawHttp.waitForPortToBeTaken(TARGET_SERVER_PORT, Duration.ofSeconds(2));
    }

    @BeforeEach
    public void startProxyServer() throws Exception {
        proxyServer = new TcpRawHttpServer(PROXY_PORT);
        proxyClient = new TcpRawHttpClient();
        proxyServer.start(clientRequest -> {
            // Rewrite the request to point the end server
            clientRequest = clientRequest.withRequestLine(
                    clientRequest.getStartLine().withHost("localhost:" + TARGET_SERVER_PORT)
            ).withHeaders(RawHttpHeaders.newBuilder()
                    // TODO merge with already existing header?
                    .with("X-Forwarded-For", clientRequest.getSenderAddress().orElseThrow(() ->
                            new IllegalStateException("client has no IP")).getHostAddress())
                    .build());

            // Forward the request to the end server
            RawHttpResponse<Void> response;
            try {
                response = proxyClient.send(clientRequest);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Return the response as it is
            return Optional.of(response);
        });

        RawHttp.waitForPortToBeTaken(PROXY_PORT, Duration.ofSeconds(2));
    }

    @AfterEach
    public void cleanup() throws IOException {
        Spark.stop();
        proxyServer.stop();
        proxyClient.close();
    }

    @Test
    public void canProxyRequests() throws IOException {
        RawHttp http = new RawHttp();

        try (TcpRawHttpClient testClient = new TcpRawHttpClient()) {

            // GET request
            RawHttpResponse<Void> resp = testClient.send(http.parseRequest(
                    "GET /hello\nHost: localhost:" + PROXY_PORT));

            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getBody().map(decodeBody()).orElse("<nothing>"), "Hello");

            // POST request
            RawHttpResponse<Void> resp2 = testClient.send(http.parseRequest(
                    "POST /echo\nHost: localhost:" + PROXY_PORT)
                    .withBody(new StringBody("hi there", "text/plain")));

            assertEquals(resp2.getStatusCode(), 200);
            assertEquals(resp2.getBody().map(decodeBody()).orElse("<nothing>"), "hi there");

            // PUT request
            RawHttpResponse<Void> resp3 = testClient.send(http.parseRequest(
                    "PUT /echo\nHost: localhost:" + PROXY_PORT)
                    .withBody(new StringBody("this is a PUT request", "text/plain")));

            assertEquals(resp3.getStatusCode(), 200);
            assertEquals(resp3.getBody().map(decodeBody()).orElse("<nothing>"), "this is a PUT request");
        }
    }

    private static Function<BodyReader, String> decodeBody() {
        return b -> {
            try {
                return b.decodeBodyToString(UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
