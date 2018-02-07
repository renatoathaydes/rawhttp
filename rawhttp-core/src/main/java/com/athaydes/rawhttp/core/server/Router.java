package com.athaydes.rawhttp.core.server;

@FunctionalInterface
public interface Router {

    RequestHandler route(String path);

}
