package rawhttp.httpcomponents;

import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.Nullable;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.StatusLine;
import rawhttp.core.body.FramedBody;
import rawhttp.core.body.LazyBodyReader;
import rawhttp.core.client.RawHttpClient;

import java.io.IOException;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

/**
 * An implementation of {@link RawHttpClient} based on the http-components library's
 * {@link CloseableHttpClient}.
 */
public class RawHttpComponentsClient implements RawHttpClient<CloseableHttpResponse> {

    private final CloseableHttpClient httpClient;
    private final RawHttp http;

    public RawHttpComponentsClient() {
        this(HttpClients.createDefault());
    }

    public RawHttpComponentsClient(CloseableHttpClient httpClient) {
        this(httpClient, new RawHttp());
    }

    public RawHttpComponentsClient(CloseableHttpClient httpClient,
                                   RawHttp http) {
        this.httpClient = httpClient;
        this.http = http;
    }

    public RawHttpResponse<CloseableHttpResponse> send(RawHttpRequest request) throws IOException {
        RequestBuilder builder = RequestBuilder.create(request.getMethod());
        builder.setUri(request.getUri());
        builder.setVersion(toProtocolVersion(request.getStartLine().getHttpVersion()));
        request.getHeaders().getHeaderNames().forEach((name) ->
                request.getHeaders().get(name).forEach(value ->
                        builder.addHeader(new BasicHeader(name, value))));

        request.getBody().ifPresent(b -> builder.setEntity(new InputStreamEntity(b.asRawStream())));

        CloseableHttpResponse response = httpClient.execute(builder.build());

        RawHttpHeaders headers = readHeaders(response);
        StatusLine statusLine = adaptStatus(response.getStatusLine());

        @Nullable LazyBodyReader body;
        if (response.getEntity() != null) {
            FramedBody framedBody = http.getFramedBody(statusLine, headers);
            body = new LazyBodyReader(framedBody, response.getEntity().getContent());
        } else {
            body = null;
        }

        return new RawHttpResponse<>(response, request, statusLine, headers, body);
    }

    private StatusLine adaptStatus(org.apache.http.StatusLine statusLine) {
        return new StatusLine(
                HttpVersion.parse(statusLine.getProtocolVersion().toString()),
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase());
    }

    private RawHttpHeaders readHeaders(CloseableHttpResponse response) {
        Header[] allHeaders = response.getAllHeaders();
        RawHttpHeaders.Builder headers = RawHttpHeaders.newBuilder();
        for (Header header : allHeaders) {
            String meta = header.getElements().length > 0 ?
                    ";" + Arrays.stream(header.getElements())
                            .flatMap(it -> Arrays.stream(it.getParameters()).map(v -> v.getName() + "=" + v.getValue()))
                            .collect(joining(";")) :
                    "";
            headers.with(header.getName(), header.getValue() + meta);
        }
        return headers.build();
    }

    private static ProtocolVersion toProtocolVersion(HttpVersion httpVersion) {
        switch (httpVersion) {
            case HTTP_0_9:
                return org.apache.http.HttpVersion.HTTP_0_9;
            case HTTP_1_0:
                return org.apache.http.HttpVersion.HTTP_1_0;
            case HTTP_1_1:
                return org.apache.http.HttpVersion.HTTP_1_1;
            default:
                throw new IllegalArgumentException("Invalid HTTP version: " + httpVersion);

        }
    }

}