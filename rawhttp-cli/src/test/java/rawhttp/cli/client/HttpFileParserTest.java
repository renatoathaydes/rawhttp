package rawhttp.cli.client;

import org.junit.Before;
import org.junit.Test;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HttpFileParserTest {

    static RawHttp HTTP = new RawHttp(RawHttpOptions.newBuilder()
            .allowIllegalStartLineCharacters()
            .build());

    private HttpFileParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new HttpFileParser(HTTP);
    }

    @Test
    public void canParseSimpleHttpRequest() throws Exception {
        String httpRequest = "GET http://example.org HTTP/1.1";
        List<HttpFileEntry> entries = parser.parse(
                new ByteArrayInputStream(httpRequest.getBytes(StandardCharsets.US_ASCII)), null);

        assertEquals(1, entries.size());

        HttpFileEntry entry = entries.get(0);

        assertNull(entry.script);
        assertEquals(HTTP.parseRequest(httpRequest).eagerly(), entry.request.eagerly());
    }

    @Test
    public void canParseMultipleHttpRequests() throws Exception {
        String request0 = "GET /something HTTP/1.1\n" +
                "Host: example.org\n" +
                "Accept: text/html\n";
        String request1 = "POST /resource/some-id HTTP/1.1\n" +
                "Host: example.org\n" +
                "Content-Type: application/json\n" +
                "\n" +
                "{\"example\": \"value\", \"count\": 1}";
        String request2 = "GET /resource/some-id HTTP/1.1\n" +
                "Host: example.org\n" +
                "Accept: application/json\n";

        String httpRequests = request0 +
                "\n" +
                "###\n" +
                request1 +
                "\n" +
                "###\n" +
                "\n" + request2;

        List<HttpFileEntry> entries = parser.parse(
                new ByteArrayInputStream(httpRequests.getBytes(StandardCharsets.US_ASCII)), null);

        assertEquals(3, entries.size());

        HttpFileEntry entry0 = entries.get(0);

        assertNull(entry0.script);
        assertEquals(HTTP.parseRequest(request0).eagerly(), entry0.request.eagerly());

        HttpFileEntry entry1 = entries.get(1);

        assertNull(entry1.script);
        assertEquals(HTTP.parseRequest(request1).eagerly(), entry1.request.eagerly());

        HttpFileEntry entry2 = entries.get(2);

        assertNull(entry2.script);
        assertEquals(HTTP.parseRequest(request2).eagerly(), entry2.request.eagerly());

    }
}
