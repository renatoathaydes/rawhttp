/**
 * RawHTTP Duplex module.
 * <p>
 * This module can be used to create a duplex communication channel as either a client or a server.
 *
 * <a href="https://renatoathaydes.github.io/rawhttp/rawhttp-modules/duplex.html">Documentation</a>
 */
module rawhttp.duplex {
    requires transitive rawhttp.core;
    exports com.athaydes.rawhttp.duplex;
    exports com.athaydes.rawhttp.duplex.body;
}
