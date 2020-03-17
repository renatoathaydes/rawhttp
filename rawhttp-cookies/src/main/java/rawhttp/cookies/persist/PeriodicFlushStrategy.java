package rawhttp.cookies.persist;

import java.net.CookieStore;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link rawhttp.cookies.persist.FileCookieJar.FlushStrategy} that will flush
 * cookies periodically.
 * <p>
 * This ensures that even in the eventuality of a JVM crash, cookies will not be lost for at least
 * the duration of the last period used by this strategy.
 * <p>
 * Even though the implementation of this class uses an {@link java.util.concurrent.ExecutorService},
 * it is not necessary to close this strategy because it uses a daemon Thread (it won't stop the JVM from
 * shutting down) and is expected to have the same lifetime as the application itself.
 */
public class PeriodicFlushStrategy extends JvmShutdownFlushStrategy {

    private static final AtomicInteger instances = new AtomicInteger();

    private final Duration period;

    // only read and write on the executor thread
    private boolean requiresFlush;

    private final ScheduledExecutorService executorService;

    public PeriodicFlushStrategy(Duration period) {
        this(period, Executors.newSingleThreadScheduledExecutor((runnable) -> {
            Thread t = new Thread(runnable, "rawhttp-periodic-flush-strategy-" + instances.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));
    }

    public PeriodicFlushStrategy(Duration period, ScheduledExecutorService executorService) {
        if (period.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Period must be at least 1 second");
        }
        this.period = period;
        this.executorService = executorService;
    }

    public Duration getPeriod() {
        return period;
    }

    @Override
    public void init(Callable<Integer> flush) {
        super.init(flush);
        executorService.scheduleAtFixedRate(() -> {
            if (requiresFlush) try {
                flush.call();
                requiresFlush = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onUpdate(CookieStore cookieStore) {
        super.onUpdate(cookieStore);
        executorService.submit(() -> {
            requiresFlush = true;
        });
    }
}
