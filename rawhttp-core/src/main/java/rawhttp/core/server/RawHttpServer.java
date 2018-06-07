package rawhttp.core.server;

/**
 * HTTP Server.
 * <p>
 * A HTTP Server can be started/stopped. Every time it is started, a new {@link Router} may be used to route
 * the HTTP requests the server receives.
 */
public interface RawHttpServer {

    /**
     * Start the server using the provided router to route requests.
     * <p>
     * If this method is called multiple times, it must replace the previous router even if it was already running,
     * and start serving requests using the new router.
     *
     * @param router request router
     */
    void start(Router router);

    /**
     * Stop the server.
     */
    void stop();

}
