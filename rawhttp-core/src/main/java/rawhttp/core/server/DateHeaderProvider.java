package rawhttp.core.server;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import rawhttp.core.RawHttpHeaders;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

final class DateHeaderProvider implements Supplier<RawHttpHeaders> {

    private final ThreadLocal<RawHttpHeaders> currentDateHeaderInSecondsResolution =
            ThreadLocal.withInitial(DateHeaderProvider::createDateHeader);

    private final ThreadLocal<Long> lastDateAccess = ThreadLocal.withInitial(() -> 0L);

    private final Supplier<RawHttpHeaders> createHeader;
    private final long maxCacheDuration;

    public DateHeaderProvider(Duration maxCacheDuration) {
        this(maxCacheDuration, DateHeaderProvider::createDateHeader);
    }

    public DateHeaderProvider(Duration maxCacheDuration, Supplier<RawHttpHeaders> createHeader) {
        this.maxCacheDuration = maxCacheDuration.toMillis();
        this.createHeader = createHeader;
    }

    private static RawHttpHeaders createDateHeader() {
        return RawHttpHeaders.newBuilderSkippingValidation()
                .with("Date", RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)))
                .build();
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
