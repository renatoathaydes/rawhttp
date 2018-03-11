/**
 * Core RawHTTP module.
 * <p>
 * This module exposes all of the HTTP specification primitives, such as requests, responses and headers.
 * <p>
 * It also contains implementations of HTTP clients (see package {@link com.athaydes.rawhttp.core.client})
 * and HTTP servers (see package {@link com.athaydes.rawhttp.core.server}).
 * <p>
 * The main entry-point to this library is the {@link com.athaydes.rawhttp.core.RawHttp} class.
 */
module com.athaydes.rawhttp.core {
    requires com.athaydes.nullable;
    exports com.athaydes.rawhttp.core;
    exports com.athaydes.rawhttp.core.client;
    exports com.athaydes.rawhttp.core.server;
    exports com.athaydes.rawhttp.core.body;
    exports com.athaydes.rawhttp.core.errors;
}
