/**
 * RawHTTP Cookies module.
 * <p>
 * Implementation of HTTP cookies, including persistence through files.
 *
 * <a href="https://renatoathaydes.github.io/rawhttp/rawhttp-modules/cookies.html">Documentation</a>
 */
module rawhttp.cookies {
    requires transitive rawhttp.core;
    exports rawhttp.cookies;
    exports rawhttp.cookies.persist;
}