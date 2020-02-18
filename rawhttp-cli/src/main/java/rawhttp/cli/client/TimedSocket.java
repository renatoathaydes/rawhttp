package rawhttp.cli.client;

import rawhttp.cli.util.RequestStatistics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

final class TimedSocket extends Socket {
    private final Socket delegate;

    private AtomicLong connectTime = new AtomicLong();
    private AtomicLong firstByteTime = new AtomicLong();
    private AtomicLong bytesRead = new AtomicLong();

    private final AtomicLong httpRequestSendTime = new AtomicLong();

    public TimedSocket(Socket delegate) {
        this.delegate = delegate;
    }

    public void markHttpRequestSendTimeNow() {
        httpRequestSendTime.set(System.nanoTime());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new TimedInputStream(delegate.getInputStream());
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        long t = System.nanoTime();
        delegate.connect(endpoint, timeout);
        connectTime.set(System.nanoTime() - t);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        long t = System.nanoTime();
        delegate.connect(endpoint);
        connectTime.set(System.nanoTime() - t);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        delegate.bind(bindpoint);
    }

    @Override
    public synchronized void close() throws IOException {
        delegate.close();
    }

    @Override
    public InetAddress getInetAddress() {
        return delegate.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public int getPort() {
        return delegate.getPort();
    }

    @Override
    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return delegate.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return delegate.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return delegate.getChannel();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        delegate.setTcpNoDelay(on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return delegate.getTcpNoDelay();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        delegate.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return delegate.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        delegate.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        delegate.setOOBInline(on);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return delegate.getOOBInline();
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        delegate.setSoTimeout(timeout);
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return delegate.getSoTimeout();
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        delegate.setSendBufferSize(size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return delegate.getSendBufferSize();
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        delegate.setReceiveBufferSize(size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return delegate.getReceiveBufferSize();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        delegate.setKeepAlive(on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return delegate.getKeepAlive();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        delegate.setTrafficClass(tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return delegate.getTrafficClass();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        delegate.setReuseAddress(on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return delegate.getReuseAddress();
    }

    @Override
    public void shutdownInput() throws IOException {
        delegate.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        delegate.shutdownOutput();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isBound() {
        return delegate.isBound();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return delegate.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return delegate.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    /**
     * Compute all statistics for the current HTTP request/response.
     * <p>
     * This method must be called at the exact time when the HTTP response has been fully received,
     * including the body.
     *
     * @return statistics for the current request/response.
     */
    public RequestStatistics computeStatistics() {
        // this assumes that the current time is the exact time when the response body has been fully received
        long responseTime = System.nanoTime() - httpRequestSendTime.get();
        return new RequestStatistics(connectTime.get(), firstByteTime.get(), responseTime, bytesRead.get());
    }

    private final class TimedInputStream extends InputStream {

        private final InputStream delegate;
        private boolean gotFirstByte = false;

        public TimedInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            if (!gotFirstByte) {
                int b = delegate.read();
                long t = httpRequestSendTime.get();
                firstByteTime.set(System.nanoTime() - t);
                gotFirstByte = true;
                return b;
            }
            bytesRead.incrementAndGet();
            return delegate.read();
        }
    }
}
