package com.athaydes.rawhttp.reqinedit.js;

import com.athaydes.rawhttp.reqinedit.HttpEnvironment;
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter;
import com.athaydes.rawhttp.reqinedit.ScriptException;
import com.athaydes.rawhttp.reqinedit.js.internal.JsTestReporter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;
import org.webjars.mustache.Mustache;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.EagerBodyReader;

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

/**
 * The default, JavaScript-based {@link HttpEnvironment} used by {@link com.athaydes.rawhttp.reqinedit.ReqInEditUnit}
 * to evaluate HTTP files.
 * <p>
 * This implementation is based on the Java Nashorn engine in an effort to keep compatibility with the JS engine
 * used in IntelliJ to run HTTP files.
 * <p>
 * Future implementations of {@link HttpEnvironment} may be based on more modern JS engines, such as GraalVM,
 * which may allow broad compatibility with JS libraries and tools.
 * <p>
 * For help writing Nashorn-based JS, please see the
 * <a href="https://winterbe.com/posts/2014/04/05/java8-nashorn-tutorial/">Nashorn Tutorial</a>.
 */
public final class JsEnvironment implements HttpEnvironment, AutoCloseable {

    private final Context jsEngine;

    @Nullable
    private final File projectDir;

    /**
     * Load the environment with the given name.
     *
     * @param httpFile the http file that requires an environment.
     * @param name     of environment
     * @return the environment
     */
    public static JsEnvironment loadEnvironment(@Nullable File httpFile,
                                                @Nullable String name) {
        return new JsEnvironment(httpFile == null ? null : httpFile.getParentFile(), name);
    }

    public JsEnvironment() {
        this(null, null);
    }

    public JsEnvironment(@Nullable File projectDir, @Nullable String environmentName) {
        this.projectDir = projectDir;

        List<String> environments = environmentName == null
                ? Collections.emptyList()
                : JsLoader.getJsObjects(projectDir);

        jsEngine = Context.newBuilder("js")
                .allowAllAccess(true)
                .allowNativeAccess(true)
                .build();

        loadLibrary(Mustache.class, "Mustache", "/META-INF/resources/webjars/mustache/4.2.0/mustache.js");

        var builder = new StringBuilder();
        readResource(JsEnvironment.class, "response_handler.js", builder);

        try {
            jsEngine.eval(Source.newBuilder("js", builder, "response_handler.js").build());
        } catch (PolyglotException | IOException e) {
            throw new ScriptException(e);
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
        return invoke("__mustacheRender__", line).asString();
    }

    Value eval(String script) throws ScriptException {
        try {
            return jsEngine.eval("js", script);
        } catch (PolyglotException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public boolean runResponseHandler(String responseHandler,
                                      RawHttpResponse<?> response,
                                      HttpTestsReporter testsReporter)
            throws IOException {
        setResponse(response.eagerly());
        eval(responseHandler);
        return runAllTests(testsReporter);
    }

    void setResponse(EagerHttpResponse<?> response) {
        Map<String, String> contentType = contentTypeObject(response.getHeaders().getFirst("Content-Type").orElse(""));
        invoke("__setResponse__", response.getStatusCode(), response.getHeaders(),
                contentType, bodyObject(contentType, response.getBody().orElse(null)));
    }

    boolean runAllTests(HttpTestsReporter reporter) {
        return invoke("__runAllTests__", new JsTestReporter(reporter)).asBoolean();
    }

    private Value invoke(String name, Object... args) {
        try {
            return jsEngine.getBindings("js")
                    .getMember(name).execute(args);
        } catch (PolyglotException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadLibrary(Class<?> typeInModule, String name, String path) {
        var builder = new StringBuilder();
        builder.append("var exports = {};var module = {};\n");
        readResource(typeInModule, path, builder);
        builder.append("\nvar ").append(name).append(" = Object.keys(exports).length > 0 ? exports : module.exports;" +
                " exports = {}; module = {};\n");
        jsEngine.eval("js", builder);
    }

    private static void readResource(Class<?> typeInModule, String path, StringBuilder builder) {
        try (InputStream s = getResourceAsStream(typeInModule, path)) {
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

    @Override
    public void close() {
        jsEngine.close();
    }

    private static InputStream getResourceAsStream(Class<?> typeInModule, String path) {
        var resource = typeInModule.getResourceAsStream(path);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to find resource in classpath: '" + path + "'");
        }
        return resource;
    }
}
