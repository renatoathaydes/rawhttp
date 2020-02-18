package rawhttp.cli.client;

import javax.script.ScriptEngine;
import java.io.IOException;
import java.util.List;

public final class HttpFileRunner {
    private final RawHttpCliClient httpClient;
    private final ScriptEngine engine;

    public HttpFileRunner(RawHttpCliClient httpClient,
                          ScriptEngine engine) {
        this.httpClient = httpClient;
        this.engine = engine;
    }

    public void run(List<HttpFileEntry> entries) throws IOException {
        System.out.println("HttpFileRunner not implemented yet...");
        for (HttpFileEntry entry : entries) {
            entry.request.writeTo(System.out);
            if (entry.script != null) {
                System.out.println("-------- JS --------");
                System.out.println(entry.script);
                System.out.println("--------------------");
            }
        }
    }
}
