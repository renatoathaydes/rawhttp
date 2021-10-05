/**
 * RawHTTP module integration with the
 * <a href="https://hc.apache.org/httpcomponents-client-4.5.x">Apache HTTP-Components</a> library.
 * <p>
 * <a href="https://renatoathaydes.github.io/rawhttp/rawhttp-modules/http-components.html">Documentation</a>
 */
module rawhttp.httpcomponents {
    requires transitive rawhttp.core;
    requires org.apache.httpcomponents.httpcore;
    requires org.apache.httpcomponents.httpclient;
    exports rawhttp.httpcomponents;
}
