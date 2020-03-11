package com.athaydes.rawhttp.reqinedit;


import rawhttp.core.RawHttpResponse;

import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A HTTP environment for evaluation of scripts in {@link ReqInEditUnit}.
 */
public interface HttpEnvironment {

    /**
     * Render a String template.
     *
     * @param template text that may contain variables of the form {@code {{ var }}}.
     * @return rendered template
     */
    String renderTemplate(String template);

    /**
     * Run the given responseHandler script using the given response.
     * <p>
     * Results are reported to the given reporter.
     *
     * @param responseHandler script to run
     * @param response        the HTTP response
     * @param reporter        to receive test results, if any
     * @throws IOException     if a problem occurs reading the HTTP response
     * @throws ScriptException if running the responseHandler script results in unexpected errors
     */
    void runResponseHandler(String responseHandler,
                            RawHttpResponse<?> response,
                            HttpTestsReporter reporter)
            throws IOException, ScriptException;

    /**
     * Resolve a path referenced from a HTTP file.
     * <p>
     * If the path is relative, it must be relative to the HTTP file.
     *
     * @param path to resolve
     * @return the resolved path
     */
    Path resolvePath(String path);
}
