package rawhttp.core.body.encoding;

import rawhttp.core.internal.Bool;

import java.io.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

final class GZipUncompressorOutputStream extends DecodingOutputStream {

    private final PipedInputStream encodedBytesReceiver;
    private final PipedOutputStream encodedBytesSink;
    private final Bool readerStarted = new Bool();
    private final Bool hasError = new Bool();
    private final ExecutorService executorService;
    private final int bufferSize;
    private Future<?> readerExecution;
    private final Thread callerThread;

    GZipUncompressorOutputStream(OutputStream out, int bufferSize) {
        super(out);
        this.bufferSize = bufferSize;
        this.encodedBytesReceiver = new PipedInputStream();
        this.encodedBytesSink = new PipedOutputStream();
        this.executorService = Executors.newSingleThreadExecutor();
        this.callerThread = Thread.currentThread();
    }

    @Override
    public void write(int b) throws IOException {
        byte[] buffer = new byte[1];
        buffer[0] = (byte) (b & 0xFF);
        write(buffer, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (!readerStarted.getAndSet(true)) {
            encodedBytesSink.connect(encodedBytesReceiver);
            startReader();
        }
        if (!hasError.get()) {
            encodedBytesSink.write(b, off, len);
        }
    }

    private void startReader() {
        readerExecution = executorService.submit(() -> {
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            try (GZIPInputStream decoderStream = new GZIPInputStream(encodedBytesReceiver)) {
                while ((bytesRead = decoderStream.read(buffer, 0, bufferSize)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                e.printStackTrace();
                hasError.set(true);
                // the caller thread needs to be unblocked by interrupting it as it won't receive any more bytes
                callerThread.interrupt();
                throw new WrappedException(e);
            }
        });
    }

    @Override
    public void flush() throws IOException {
        encodedBytesSink.flush();
        super.flush();
    }

    @Override
    public void finishDecoding() throws IOException {
        try {
            super.finishDecoding();
            closeQuietly(encodedBytesSink);
            readerExecution.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WrappedException) {
                cause = ((WrappedException) cause).cause;
            }
            throw new IOException(cause);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout waiting for stream to close");
        } finally {
            closeQuietly(encodedBytesReceiver);
            closeQuietly(encodedBytesSink);

            executorService.shutdownNow();
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore errors closing streams
        }
    }

    private static final class WrappedException extends RuntimeException {
        final Exception cause;

        WrappedException(Exception cause) {
            this.cause = cause;
        }
    }

}
