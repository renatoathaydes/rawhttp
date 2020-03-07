package com.athaydes.rawhttp.reqinedit.js;

import com.athaydes.rawhttp.reqinedit.HttpEnvironment;
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.EagerBodyReader;

import javax.annotation.Nullable;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public final class JsEnvironment implements HttpEnvironment {

    private final NashornScriptEngine jsEngine;

    @Nullable
    private final File projectDir;

    public JsEnvironment() {
        this(null, null);
    }

    public JsEnvironment(@Nullable File projectDir, @Nullable String environmentName) {
        this.projectDir = projectDir;

        List<String> environments = environmentName == null
                ? Collections.emptyList()
                : JsLoader.getJsObjects(projectDir);

        jsEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
        StringBuilder builder = new StringBuilder();
        loadLibraryInto(builder, "Mustache", "/META-INF/resources/webjars/mustache/3.1.0/mustache.js");
        readResource("response_handler.js", builder);

        try {
            jsEngine.eval(builder.toString());
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }

        if (!environments.isEmpty()) {
            invoke("__loadEnvironment__",
                    environmentName, environments.get(0), environments.get(1));
        }
    }

    @Override
    public Path resolvePath(String path) {
        return projectDir == null || path.startsWith("/")
                ? Paths.get(path)
                : Paths.get(projectDir.getAbsolutePath(), path);
    }

    @Override
    public String renderTemplate(String line) {
        return invoke("__mustacheRender__", line).toString();
    }

    Object eval(String script) throws ScriptException {
        return jsEngine.eval(script);
    }

    @Override
    public void runResponseHandler(String responseHandler,
                                   RawHttpResponse<?> response,
                                   HttpTestsReporter testsReporter)
            throws IOException, ScriptException {
        setResponse(response.eagerly());
        eval(responseHandler);
        runAllTests(testsReporter);
    }

    void setResponse(EagerHttpResponse<?> response) {
        Map<String, String> contentType = contentTypeObject(response.getHeaders().getFirst("Content-Type").orElse(""));
        invoke("__setResponse__", response.getStatusCode(), response.getHeaders(),
                contentType, bodyObject(contentType, response.getBody().orElse(null)));
    }

    void runAllTests(HttpTestsReporter reporter) {
        invoke("__runAllTests__", reporter);
    }

    private Object invoke(String name, Object... args) {
        try {
            return jsEngine.invokeFunction(name, args);
        } catch (ScriptException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadLibraryInto(StringBuilder builder, String name, String path) {
        builder.append("var exports = {};var module = {};\n");
        readResource(path, builder);
        builder.append("\nvar ").append(name).append(" = Object.keys(exports).length > 0 ? exports : module.exports;" +
                " exports = {}; module = {};\n");
    }

    private static void readResource(String path, StringBuilder builder) {
        try (InputStream s = JsEnvironment.class.getResourceAsStream(path)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(s, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> contentTypeObject(String contentType) {
        Map<String, String> object = new HashMap<>(2);
        object.put("mimeType", contentType);

        if (!contentType.isEmpty()) {
            String[] components = contentType.split(";");
            if (components.length > 1) {
                object.put("mimeType", components[0]);
                for (int i = 1; i < components.length; i++) {
                    String[] parts = components[i].split("=");
                    if (parts.length == 2) {
                        String attrName = parts[0].trim();
                        if (attrName.equals("charset")) {
                            object.put("charset", parts[1].trim());
                            break;
                        }
                    }
                }
            }
        }

        return object;
    }

    private Callable<String> bodyObject(Map<String, String> contentType, @Nullable EagerBodyReader body) {
        if (body == null) {
            return () -> "";
        }
        return () -> body.decodeBodyToString(Charset.forName(contentType.getOrDefault("charset", "UTF-8")));
    }

}
