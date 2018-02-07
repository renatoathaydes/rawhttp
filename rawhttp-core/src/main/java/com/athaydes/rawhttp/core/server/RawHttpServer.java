package com.athaydes.rawhttp.core.server;

public interface RawHttpServer {

    void start(Router router);

    void stop();

}
