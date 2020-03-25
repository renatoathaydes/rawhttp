package rawhttp.cookies.persist;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * An implementation of {@link CookieStore} that persists cookies in a file.
 * <p>
 * It follows logic similar to a browser:
 *
 * <ul>
 *     <li>only persistent cookies are written out to the file (non-persistent cookies are only stored in memory).</li>
 *     <li>expired cookies are eventually deleted, but never returned when queried.</li>
 * </ul>
 * <p>
 * How often cookies are flushed to the file depends on the implementation of {@link FlushStrategy} used by this
 * cookie jar. By default, a {@link JvmShutdownFlushStrategy} is used.
 */
public class FileCookieJar implements CookieStore {

    /**
     * Strategy for how a {@link FileCookieJar} should flush cookies to the file.
     */
    public interface FlushStrategy {
        /**
         * Initialize this strategy.
         * <p>
         * The given {@code flush} callable should be called every time the cookies should be flushed to a file.
         *
         * @param flush a callable that should be called to flush the cookies
         */
        void init(Callable<Integer> flush);

        /**
         * This method is called every time the {@link FileCookieJar} is modified.
         *
         * @param cookieStore a view of the in-memory cookie store backing the {@link FileCookieJar}.
         */
        void onUpdate(CookieStore cookieStore);
    }

    private final File file;
    private final FlushStrategy flushPolicy;
    private final Map<URI, List<HttpCookie>> persistentCookies = new HashMap<>();

    // the InMemory implementation is not exposed, but we can steal it by creating a CookieManager like this:
    private final CookieStore inMemory = new CookieManager().getCookieStore();

    public FileCookieJar(File file) throws IOException {
        this(file, new JvmShutdownFlushStrategy());
    }

    public FileCookieJar(File file, FlushStrategy flushPolicy) throws IOException {
        this.file = file;
        this.flushPolicy = flushPolicy;
        flushPolicy.init(this::flush);
        load();
    }

    public File getFile() {
        return file;
    }

    public FlushStrategy getFlushPolicy() {
        return flushPolicy;
    }

    private void load() throws IOException {

        if (!file.exists()) {
            return;
        }
        URI currentURI = null;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith(" ")) {
                if (currentURI == null) {
                    throw new IllegalStateException("Invalid syntax: cookie line found before a matching URI");
                }
                HttpCookie cookie = readCookie(line);
                if (cookie != null) {
                    add(currentURI, cookie);
                }
            } else {
                currentURI = URI.create(line);
            }
        }
    }

    private int flush() throws IOException {
        int count = 0;

        try (FileWriter writer = new FileWriter(file)) {
            for (Map.Entry<URI, List<HttpCookie>> entry : persistentCookies.entrySet()) {
                URI uri = entry.getKey();
                writer.write(uri.toString());
                writer.write('\n');
                for (HttpCookie httpCookie : entry.getValue()) {
                    long maxAge = httpCookie.getMaxAge();
                    if (maxAge > 0 && !httpCookie.getDiscard()) {
                        long expiresAt = maxAge + System.currentTimeMillis() / 1000L;
                        writer.write(' ');
                        writeCookie(writer, httpCookie, expiresAt);
                        writer.write('\n');
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static HttpCookie readCookie(String line) {
        String[] parts = line.split("\"");
        assert parts.length == 20;
        long maxAge = Long.parseLong(parts[19]) - System.currentTimeMillis() / 1000L;
        if (maxAge < 0L) return null;
        HttpCookie cookie = new HttpCookie(parts[1], parts[3]);
        if (!parts[5].isEmpty()) cookie.setDomain(parts[5]);
        if (!parts[7].isEmpty()) cookie.setPath(parts[7]);
        if (!parts[9].isEmpty()) cookie.setComment(parts[9]);
        if (!parts[11].isEmpty()) cookie.setCommentURL(parts[11]);
        if (!parts[13].isEmpty()) cookie.setPortlist(parts[13]);
        cookie.setVersion(Integer.parseInt(parts[15]));
        cookie.setSecure(Boolean.parseBoolean(parts[17]));
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private static void writeCookie(FileWriter writer, HttpCookie httpCookie, long expiresAt) throws IOException {
        writer.write(persistentValue(httpCookie.getName(), true));
        writer.write(persistentValue(httpCookie.getValue(), true));
        writer.write(persistentValue(httpCookie.getDomain(), true));
        writer.write(persistentValue(httpCookie.getPath(), true));
        writer.write(persistentValue(httpCookie.getComment(), true));
        writer.write(persistentValue(httpCookie.getCommentURL(), true));
        writer.write(persistentValue(httpCookie.getPortlist(), true));
        writer.write(persistentValue(httpCookie.getVersion(), true));
        writer.write(persistentValue(httpCookie.getSecure(), true));
        writer.write(persistentValue(expiresAt, false));
    }

    private static String persistentValue(Object obj, boolean trailingSpace) {
        String value;
        if (obj == null) value = "\"\"";
        else value = "\"" + obj + "\"";
        if (trailingSpace) return value + ' ';
        return value;
    }

    ///////// Write methods /////////

    @Override
    public void add(URI uri, HttpCookie cookie) {
        inMemory.add(uri, cookie);
        if (cookie.getMaxAge() > 0) {
            persistentCookies.computeIfAbsent(uri, (u) -> new ArrayList<>(4)).add(cookie);
            flushPolicy.onUpdate(inMemory);
        }
    }

    @Override
    public boolean remove(URI uri, HttpCookie cookie) {
        boolean result = inMemory.remove(uri, cookie);
        List<HttpCookie> stored = persistentCookies.get(uri);
        if (stored != null && stored.remove(cookie)) {
            flushPolicy.onUpdate(inMemory);
            if (stored.isEmpty()) {
                persistentCookies.remove(uri);
            }
        }
        return result;
    }

    @Override
    public boolean removeAll() {
        boolean result = inMemory.removeAll();
        if (!persistentCookies.isEmpty()) {
            persistentCookies.clear();
            flushPolicy.onUpdate(inMemory);
        }
        return result;
    }

    ///////// Read-only methods /////////

    @Override
    public List<HttpCookie> get(URI uri) {
        return inMemory.get(uri);
    }

    @Override
    public List<HttpCookie> getCookies() {
        return inMemory.getCookies();
    }

    @Override
    public List<URI> getURIs() {
        return inMemory.getURIs();
    }
}
