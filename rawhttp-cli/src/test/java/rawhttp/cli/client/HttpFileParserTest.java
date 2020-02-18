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
}
