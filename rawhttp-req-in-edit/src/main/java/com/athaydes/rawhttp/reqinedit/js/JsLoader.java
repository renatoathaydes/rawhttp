package com.athaydes.rawhttp.reqinedit.js;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class JsLoader {

    /**
     * Returns two JS Objects, the first being the public environment,
     * the second being the private environment.
     *
     * @param projectDir where the HTTP file is located
     * @return the public and private environments
     */
    static List<String> getJsObjects(@Nullable File projectDir) {
        if (projectDir == null) {
            projectDir = new File(".");
        }

        try {
            return load(new File(projectDir, "rest-client.env.json"),
                    new File(projectDir, "http-client.env.json"),
                    new File(projectDir, "rest-client.private.env.json"),
                    new File(projectDir, "http-client.private.env.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> load(File restClientEnv, File httpClientEnv,
                             File privateRestClientEnv, File privateHttpClientEnv) throws IOException {
        List<String> jsonObjects = new ArrayList<>(2);
        if (restClientEnv.isFile()) {
            jsonObjects.add(read(restClientEnv));
        } else if (httpClientEnv.isFile()) {
            jsonObjects.add(read(httpClientEnv));
        } else {
            jsonObjects.add("{}");
        }

        if (privateRestClientEnv.isFile()) {
            jsonObjects.add(read(privateRestClientEnv));
        } else if (privateHttpClientEnv.isFile()) {
            jsonObjects.add(read(privateHttpClientEnv));
        } else {
            jsonObjects.add("{}");
        }

        return jsonObjects;
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
