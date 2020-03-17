package rawhttp.cookies.persist;

import java.net.CookieStore;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of {@link rawhttp.cookies.persist.FileCookieJar.FlushStrategy} that will
 * only flush cookies when the JVM shuts down.
 * <p>
 * Notice that it will only flush cookies if the JVM has a chance to call the shut-down hooks, which is not
 * guaranteed in case of crashes or if the process is forcibly killed.
 * <p>
 * If losing the cookies could cause serious issues, use one of the other strategies in this package.
 */
public class JvmShutdownFlushStrategy implements FileCookieJar.FlushStrategy {

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    @Override
    public void init(Callable<Integer> flush) {
        if (isInitialized.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    flush.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        } else {
            throw new IllegalStateException("Already initialized");
        }
    }

    @Override
    public void onUpdate(CookieStore cookieStore) {
        // nothing to do, only persist on JVM shutdown
    }
}
