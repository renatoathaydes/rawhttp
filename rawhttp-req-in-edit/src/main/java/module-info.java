/**
 * RawHTTP Req-In-Edit Module.
 *
 * This module implements a runner for HTTP files written in the
 * <a href="https://github.com/JetBrains/http-request-in-editor-spec">Request In Editor Specification</a>
 * format created by JetBrains to make it easier to execute HTTP requests and test their responses.
 *
 * <a href="https://renatoathaydes.github.io/rawhttp/rawhttp-modules/req-in-edit.html">Documentation</a>
 */
module rawhttp.req_in_edit {
    requires transitive rawhttp.core;
    requires transitive rawhttp.cookies;
    requires org.graalvm.sdk;
    exports com.athaydes.rawhttp.reqinedit;
    exports com.athaydes.rawhttp.reqinedit.js;
    exports com.athaydes.rawhttp.reqinedit.js.internal;
}