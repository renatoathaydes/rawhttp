package rawhttp.cookies.persist;

import java.net.CookieStore;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link rawhttp.cookies.persist.FileCookieJar.FlushStrategy} that flushes every
 * {@code N} updates, where {@code N >= 1}.
 * <p>
 * As this class extends from {@link JvmShutdownFlushStrategy}, it will also flush just before the JVM
 * shuts down, if possible.
 */
public class OnWriteFlushStrategy extends JvmShutdownFlushStrategy {

    private final int writeCount;
    private final AtomicInteger totalWrites = new AtomicInteger();
    private volatile Callable<Integer> flush;

    /**
     * Creates a flush strategy that flushes the cookies on every single update.
     */
    public OnWriteFlushStrategy() {
        this(1);
    }

    /**
     * Creates a flush strategy that flushes the cookies on every {@code writeCount} update.
     *
     * @param writeCount number of writes between successive flushes. Must be {@code >= 1}.
     */
    public OnWriteFlushStrategy(int writeCount) {
        if (writeCount < 1) {
            throw new IllegalArgumentException("writeCount must be at least 1");
        }
        this.writeCount = writeCount;
    }

    @Override
    public void init(Callable<Integer> flush) {
        super.init(flush);
        this.flush = flush;
    }

    @Override
    public void onUpdate(CookieStore cookieStore) {
        super.onUpdate(cookieStore);
        int count = totalWrites.incrementAndGet();
        if (count % writeCount == 0) {
            try {
                flush.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
