package rawhttp.cli;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

public interface RequestLogger {
    void logRequest(RawHttpRequest request,
                    RawHttpResponse<?> response);
}

final class NoopRequestLogger implements RequestLogger {
    @Override
    public void logRequest(RawHttpRequest request, RawHttpResponse<?> response) {
        // noop
    }
}

final class AsyncSysoutRequestLogger implements RequestLogger {

    private static final DateTimeFormatter dateFormat = DateTimeFormatter
            .ofPattern("d/MMM/yyyy:HH:mm:ss z")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("async-sysout-request-logger");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void logRequest(RawHttpRequest request, RawHttpResponse<?> response) {
        executor.submit(() -> {
            if (request.getSenderAddress().isPresent()) {
                InetAddress senderAddress = request.getSenderAddress().get();
                System.out.print(senderAddress.getHostAddress() + " ");
            }
            Long bytes = response.getBody()
                    .map(b -> b.getLengthIfKnown().orElse(-1L))
                    .orElse(-1L);
            System.out.println("[" + LocalDateTime.now().format(dateFormat) + "] \"" +
                    request.getStartLine() + "\" " + response.getStatusCode() +
                    " " + bytes);
        });
    }

}

