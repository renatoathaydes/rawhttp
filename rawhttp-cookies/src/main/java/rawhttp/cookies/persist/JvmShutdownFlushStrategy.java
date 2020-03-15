package rawhttp.cookies.persist;

import java.net.CookieStore;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

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
