package rawhttp.cli.time;

import rawhttp.core.Writable;

import java.io.OutputStream;
import java.io.PrintWriter;

public final class RequestStatistics implements Writable {
    private final long connectTime, ttfbTime, responseTime, bytesRead;

    public RequestStatistics(long connectTime, long ttfbTime, long responseTime, long bytesRead) {
        this.connectTime = connectTime;
        this.bytesRead = bytesRead;
        this.ttfbTime = ttfbTime;
        this.responseTime = responseTime;
    }

    @Override
    public void writeTo(OutputStream outputStream) {
        PrintWriter writer = new PrintWriter(outputStream);
        try {
            writer.print("Connect time: ");
            writer.printf("%.2f ms%n", connectTime * 0.000001);
            writer.print("First received byte time: ");
            writer.printf("%.2f ms%n", ttfbTime * 0.000001);
            writer.print("Total response time: ");
            writer.printf("%.2f ms%n", responseTime * 0.000001);
            writer.print("Bytes received: ");
            writer.println(bytesRead);
            writer.print("Throughput (bytes/sec): ");
            writer.printf("%.0f%n", bytesRead / ((responseTime - ttfbTime) * 0.000000001));
        } finally {
            writer.flush();
        }
    }
}
