package com.athaydes.rawhttp.reqinedit.js;

import com.athaydes.rawhttp.reqinedit.HttpEnvironment;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.annotation.Nullable;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public final class JsEnvironment implements HttpEnvironment {

    private final NashornScriptEngine jsEngine;

    public JsEnvironment() {
        this(null, null);
    }

    public JsEnvironment(@Nullable File projectDir, @Nullable String environmentName) {
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
    public String apply(String line) {
        return invoke("__mustacheRender__", line).toString();
    }

    Object eval(String script) throws ScriptException {
        return jsEngine.eval(script);
    }

    List<?> runAllTests() {
        return (List<?>) invoke("__runAllTests__");
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
        builder.append("\nvar ").append(name).append(" = Object.keys(exports).length > 0 ? exports : module.exports; exports = {}; module = {};\n");
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

}
