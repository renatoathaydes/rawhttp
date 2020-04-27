package com.athaydes.rawhttp.reqinedit.js;

import com.athaydes.rawhttp.reqinedit.HttpTestResult;
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

@SuppressWarnings("unused") // used from the JS code
public final class JsTestReporter {
    private final HttpTestsReporter httpTestsReporter;

    public JsTestReporter(HttpTestsReporter httpTestsReporter) {
        this.httpTestsReporter = httpTestsReporter;
    }

    public void success(ScriptObjectMirror objectMirror) {
        long endTime = System.currentTimeMillis();
        httpTestsReporter.report(new HttpTestResult(
                (String) objectMirror.get("name"),
                (long) objectMirror.get("time"),
                endTime,
                null));
    }

    public void failure(ScriptObjectMirror objectMirror) {
        long endTime = System.currentTimeMillis();
        httpTestsReporter.report(new HttpTestResult(
                (String) objectMirror.get("name"),
                (long) objectMirror.get("time"),
                endTime,
                objectMirror.get("error").toString()));
    }
}
