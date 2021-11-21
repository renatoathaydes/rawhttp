import rawhttp.core.body.encoding.ChunkDecoder;
import rawhttp.core.body.encoding.DeflateDecoder;
import rawhttp.core.body.encoding.GzipDecoder;
import rawhttp.core.body.encoding.HttpMessageDecoder;
import rawhttp.core.body.encoding.IdentityDecoder;

/**
 * RawHTTP Core module.
 * <p>
 * Implements basic HTTP message parsing and networking capabilities.
 * It also contains simple implementations of a HTTP server and client.
 *
 * <a href="https://renatoathaydes.github.io/rawhttp/docs/index.html">Documentation</a>
 */
module rawhttp.core {
    requires static transitive org.jetbrains.annotations;
    exports rawhttp.core;
    exports rawhttp.core.body;
    exports rawhttp.core.client;
    exports rawhttp.core.errors;
    exports rawhttp.core.server;
    exports rawhttp.core.body.encoding;
    provides HttpMessageDecoder
            with ChunkDecoder, GzipDecoder, DeflateDecoder, IdentityDecoder;
    uses HttpMessageDecoder;
}
