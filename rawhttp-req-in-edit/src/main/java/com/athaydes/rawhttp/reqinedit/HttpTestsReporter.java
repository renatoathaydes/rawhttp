package com.athaydes.rawhttp.reqinedit;

import java.io.Closeable;
import java.io.IOException;

/**
 * Generator of reports for a {@link ReqInEditUnit}.
 */
public interface HttpTestsReporter extends Closeable {
    /**
     * Report the result of a single test run in {@link ReqInEditUnit}.
     *
     * @param result a single test result
     */
    void report(HttpTestResult result);

    /**
     * Close this reporter.
     * <p>
     * This method is normally called once all tests's results have been reported.
     * <p>
     * The default implementation does nothing.
     *
     * @throws IOException if a problem occurs closing this
     */
    default void close() throws IOException {
    }
}
