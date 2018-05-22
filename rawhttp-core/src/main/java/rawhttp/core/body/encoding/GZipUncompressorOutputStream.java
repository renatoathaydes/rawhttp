package rawhttp.core.body.encoding;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

final class GZipUncompressorOutputStream extends DecodingOutputStream {

    private final PipedInputStream pipedInputStream;
    private final PipedOutputStream pipedOutputStream;
    private final AtomicBoolean readerRunning = new AtomicBoolean(false);
    private final ExecutorService executorService;
    private final int bufferSize;

    GZipUncompressorOutputStream(OutputStream out, int bufferSize) {
        super(out);
        this.bufferSize = bufferSize;
        this.pipedInputStream = new PipedInputStream();
        this.pipedOutputStream = new PipedOutputStream();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void write(int b) throws IOException {
        byte[] buffer = new byte[1];
        buffer[0] = (byte) (b & 0xFF);
        write(buffer, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (!readerRunning.getAndSet(true)) {
            pipedOutputStream.connect(pipedInputStream);
            startReader();
        }
        pipedOutputStream.write(b, off, len);
    }

    private void startReader() {
        executorService.submit(() -> {
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            try (GZIPInputStream decoderStream = new GZIPInputStream(pipedInputStream)) {
                while ((bytesRead = decoderStream.read(buffer, 0, bufferSize)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void flush() throws IOException {
        pipedOutputStream.flush();
        super.flush();
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            throw new RuntimeException(e);
        }
        super.close();
    }
}