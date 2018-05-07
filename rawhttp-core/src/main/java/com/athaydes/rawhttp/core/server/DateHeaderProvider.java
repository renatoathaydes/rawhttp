package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.RawHttpHeaders;
import java.time.Duration;
import java.util.function.Supplier;

final class DateHeaderProvider implements Supplier<RawHttpHeaders> {

    private final ThreadLocal<RawHttpHeaders> currentDateHeaderInSecondsResolution =
            ThreadLocal.withInitial(TcpRawHttpServer::createDateHeader);

    private final ThreadLocal<Long> lastDateAccess = ThreadLocal.withInitial(() -> 0L);

    private final Supplier<RawHttpHeaders> createHeader;
    private final long maxCacheDuration;

    public DateHeaderProvider(Duration maxCacheDuration) {
        this(maxCacheDuration, TcpRawHttpServer::createDateHeader);
    }

    public DateHeaderProvider(Duration maxCacheDuration, Supplier<RawHttpHeaders> createHeader) {
        this.maxCacheDuration = maxCacheDuration.toMillis();
        this.createHeader = createHeader;
    }

    @Override
    public RawHttpHeaders get() {
        final long now = System.currentTimeMillis();
        final RawHttpHeaders dateHeader;
        if (lastDateAccess.get() < now - maxCacheDuration) {
            dateHeader = createHeader.get();
            lastDateAccess.set(now);
            currentDateHeaderInSecondsResolution.set(dateHeader);
        } else {
            dateHeader = currentDateHeaderInSecondsResolution.get();
        }
        return dateHeader;
    }

}
