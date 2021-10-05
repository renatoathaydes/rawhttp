package rawhttp.cli.client;

import org.jetbrains.annotations.Nullable;
import rawhttp.cli.PrintResponseMode;
import rawhttp.cli.util.RequestStatistics;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.StatusLine;
import rawhttp.core.body.BodyReader;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ResponsePrinter prints the requested HTTP response message parts according to the chosen
 * {@link PrintResponseMode}.
 * <p>
 * All print operations must run async to avoid impacting time measurements.
 */
public interface ResponsePrinter extends Closeable {
    default void print(StatusLine statusLine) {
    }

    default void print(RawHttpHeaders headers) {
    }

    default void print(@Nullable BodyReader bodyReader) {
        if (bodyReader == null) return;
        try {
            // consume but do not print
            bodyReader.eager();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    default void printStats(RequestStatistics statistics) {
    }

    static ResponsePrinter of(PrintResponseMode printResponseMode) {
        AllResponsePrinter allPrinter = new AllResponsePrinter();

        switch (printResponseMode) {
            case RESPONSE:
                return new FullResponsePrinter(allPrinter);
            case ALL:
                return allPrinter;
            case BODY:
                return new BodyResponsePrinter(allPrinter);
            case STATUS:
                return new StatusOnlyPrinter(allPrinter);
            case STATS:
                return new StatsPrinter(allPrinter);
        }
        throw new RuntimeException("PrintResponseMode option not covered: " + printResponseMode.name());
    }

    /**
     * Wait for all print requests to complete.
     */
    void waitFor();
}

class AllResponsePrinter implements ResponsePrinter {

    private final List<Future<?>> allRequests = new ArrayList<>(4);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    protected void runAsync(Runnable r) {
        allRequests.add(executorService.submit(r));
    }

    @Override
    public void print(StatusLine statusLine) {
        runAsync(() -> {
            try {
                statusLine.writeTo(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void print(RawHttpHeaders headers) {
        runAsync(() -> {
            try {
                headers.writeTo(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void print(@Nullable BodyReader bodyReader) {
        if (bodyReader == null) return;
        runAsync(() -> {
            try {
                bodyReader.writeDecodedTo(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // print a new-line after the response body
                System.out.println();
            }
        });
    }

    @Override
    public void printStats(RequestStatistics statistics) {
        printStats(statistics, true);
    }

    void printStats(RequestStatistics statistics, boolean printSeparators) {
        runAsync(() -> {
            if (printSeparators) {
                System.out.println();
                System.out.println("---------------------------------");
            }
            statistics.writeTo(System.out);
        });
    }

    @Override
    public void waitFor() {
        try {
            for (Future<?> request : allRequests) {
                try {
                    request.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting for results to be printed out");
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    System.err.println("Timed out waiting for results to be printed out");
                }
            }
        } finally {
            allRequests.clear();
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}

class FullResponsePrinter implements ResponsePrinter {

    private final AllResponsePrinter allResponsePrinter;

    public FullResponsePrinter(AllResponsePrinter allResponsePrinter) {
        this.allResponsePrinter = allResponsePrinter;
    }

    @Override
    public void print(StatusLine statusLine) {
        allResponsePrinter.print(statusLine);
    }

    @Override
    public void print(RawHttpHeaders headers) {
        allResponsePrinter.print(headers);
    }

    @Override
    public void print(@Nullable BodyReader bodyReader) {
        allResponsePrinter.print(bodyReader);
    }

    @Override
    public void waitFor() {
        allResponsePrinter.waitFor();
    }

    @Override
    public void close() {
        allResponsePrinter.close();
    }
}

final class StatusOnlyPrinter implements ResponsePrinter {
    private final AllResponsePrinter allResponsePrinter;

    public StatusOnlyPrinter(AllResponsePrinter allResponsePrinter) {
        this.allResponsePrinter = allResponsePrinter;
    }

    @Override
    public void print(StatusLine statusLine) {
        allResponsePrinter.print(statusLine);
    }

    @Override
    public void waitFor() {
        allResponsePrinter.waitFor();
    }

    @Override
    public void close() {
        allResponsePrinter.close();
    }

}

final class BodyResponsePrinter implements ResponsePrinter {
    private final AllResponsePrinter allResponsePrinter;

    BodyResponsePrinter(AllResponsePrinter allResponsePrinter) {
        this.allResponsePrinter = allResponsePrinter;
    }

    @Override
    public void print(@Nullable BodyReader bodyReader) {
        allResponsePrinter.print(bodyReader);
    }

    @Override
    public void waitFor() {
        allResponsePrinter.waitFor();
    }

    @Override
    public void close() {
        allResponsePrinter.close();
    }

}

final class StatsPrinter implements ResponsePrinter {

    private final AllResponsePrinter allResponsePrinter;

    StatsPrinter(AllResponsePrinter allResponsePrinter) {
        this.allResponsePrinter = allResponsePrinter;
    }

    @Override
    public void printStats(RequestStatistics statistics) {
        allResponsePrinter.printStats(statistics, false);
    }

    @Override
    public void waitFor() {
        allResponsePrinter.waitFor();
    }

    @Override
    public void close() {
        allResponsePrinter.close();
    }
}
