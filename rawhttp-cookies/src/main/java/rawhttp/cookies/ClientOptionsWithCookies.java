package rawhttp.cookies;

import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class ClientOptionsWithCookies implements TcpRawHttpClient.TcpRawHttpClientOptions {

    private final CookieHandler cookieHandler;
    private final TcpRawHttpClient.TcpRawHttpClientOptions delegate;

    public ClientOptionsWithCookies() {
        this(new CookieManager(), new TcpRawHttpClient.DefaultOptions());
    }

    public ClientOptionsWithCookies(CookieHandler cookieHandler) {
        this(cookieHandler, new TcpRawHttpClient.DefaultOptions());
    }

    public ClientOptionsWithCookies(CookieHandler cookieHandler,
                                    TcpRawHttpClient.TcpRawHttpClientOptions delegate) {
        this.cookieHandler = cookieHandler;
        this.delegate = delegate;
    }

    @Override
    public Socket getSocket(URI uri) {
        return delegate.getSocket(uri);
    }

    @Override
    public ExecutorService getExecutorService() {
        return delegate.getExecutorService();
    }

    @Override
    public RawHttpRequest onRequest(RawHttpRequest httpRequest) throws IOException {
        return delegate.onRequest(addCookies(httpRequest));
    }

    @Override
    public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
        cookieHandler.put(uri, httpResponse.getHeaders().asMap());
        return delegate.onResponse(socket, uri, httpResponse);
    }

    private RawHttpRequest addCookies(RawHttpRequest request) throws IOException {
        RawHttpHeaders headers = request.getHeaders();
        Set<Map.Entry<String, List<String>>> cookies = cookieHandler.get(request.getUri(), headers.asMap()).entrySet();

        if (!cookies.isEmpty()) {
            RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder(headers);
            for (Map.Entry<String, List<String>> entry : cookies) {
                String cookieHeaderName = entry.getKey();
                List<String> values = entry.getValue();
                builder.with(cookieHeaderName, String.join("; ", values));
            }
            return request.withHeaders(builder.build());
        }

        return request;
    }

}
