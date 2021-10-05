package com.athaydes.rawhttp.reqinedit.js.internal;

import com.athaydes.rawhttp.reqinedit.HttpTestResult;
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unused") // used from the JS code
public final class JsTestReporter {
    private final HttpTestsReporter httpTestsReporter;

    public JsTestReporter(HttpTestsReporter httpTestsReporter) {
        this.httpTestsReporter = httpTestsReporter;
    }

    public void success(Value objectMirror) {
        long endTime = System.currentTimeMillis();
        var name = objectMirror.getMember("name").asString();
        var time = objectMirror.getMember("time").asLong();
        httpTestsReporter.report(new HttpTestResult(name, time, endTime, null));
    }

    public void failure(Value objectMirror) {
        long endTime = System.currentTimeMillis();
        var name = objectMirror.getMember("name").asString();
        var time = objectMirror.getMember("time").asLong();
        var error = objectMirror.getMember("error").asString();
        httpTestsReporter.report(new HttpTestResult(name, time, endTime, error));
    }
}
