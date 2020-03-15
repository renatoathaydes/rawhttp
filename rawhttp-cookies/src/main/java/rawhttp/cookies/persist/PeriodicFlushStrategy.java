package rawhttp.cookies.persist;

import java.net.CookieStore;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
