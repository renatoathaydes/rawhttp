package com.athaydes.rawhttp.reqinedit;


import rawhttp.core.RawHttpResponse;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.List;

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
     *
     * @param responseHandler script to run
     * @param response        the HTTP response
     * @return list of errors thrown by the responseHandler
     * @throws IOException     if a problem occurs reading the HTTP response
     * @throws ScriptException if running the responseHandler script results in unexpected errors
     */
    List<String> runResponseHandler(String responseHandler, RawHttpResponse<?> response)
            throws IOException, ScriptException;
}
