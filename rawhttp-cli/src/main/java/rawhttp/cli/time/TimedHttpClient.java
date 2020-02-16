package rawhttp.cli.time;

import rawhttp.cli.PrintResponseMode;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimedHttpClient extends TcpRawHttpClient {

    public TimedHttpClient(PrintResponseMode printResponseMode) {
        super(new ClientOptions(ResponsePrinter.of(printResponseMode)));
    }

    @Override
    protected Runnable requestSender(RawHttpRequest request, OutputStream outputStream, boolean expectContinue) {
        return new TimedRunnable(super.requestSender(request, outputStream, expectContinue));
    }

    private ClientOptions getOptions() {
        return (ClientOptions) options;
    }

    private final class TimedRunnable implements Runnable {
        private final Runnable delegate;

        public TimedRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            getOptions().updateSendTime();
            delegate.run();
        }
    }

    private static final class ClientOptions implements TcpRawHttpClientOptions {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        private final ResponsePrinter responsePrinter;
        private TimedSocket currentSocket;

        public ClientOptions(ResponsePrinter responsePrinter) {
            this.responsePrinter = responsePrinter;
        }

        void updateSendTime() {
            currentSocket.markHttpRequestSendTimeNow();
        }

        @Override
        public TimedSocket getSocket(URI uri) {
            boolean useHttps = "https".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            int port = uri.getPort();
            if (port < 1) {
                port = useHttps ? 443 : 80;
            }
            InetAddress address;
            try {
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            Socket socket;
            try {
                socket = useHttps
                        ? SSLSocketFactory.getDefault().createSocket()
                        : new Socket();
                socket.setSoTimeout(5_000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            currentSocket = new TimedSocket(socket);
            try {
                currentSocket.connect(new InetSocketAddress(address, port), 10_000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return currentSocket;
        }

        @Override
        public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
            assert socket == currentSocket;
            if (httpResponse.getStatusCode() == 100) {
                responsePrinter.print(httpResponse.getStartLine());
                return httpResponse;
            }

            responsePrinter.print(httpResponse.getStartLine());
            responsePrinter.print(httpResponse.getHeaders());

            boolean keepAlive = !RawHttpResponse.shouldCloseConnectionAfter(httpResponse);
            EagerHttpResponse<Void> eagerResponse = httpResponse.eagerly(keepAlive);
            RequestStatistics stats = currentSocket.computeStatistics();

            responsePrinter.print(eagerResponse.getBody().orElse(null));
            responsePrinter.printStats(stats);

            responsePrinter.waitFor();

            if (!keepAlive) {
                socket.close();
            }

            return eagerResponse;
        }

        @Override
        public ExecutorService getExecutorService() {
            return executorService;
        }

        @Override
        public void close() {
            executorService.shutdown();
        }

        @Override
        public void removeSocket(Socket socket) {
        }
    }
}
