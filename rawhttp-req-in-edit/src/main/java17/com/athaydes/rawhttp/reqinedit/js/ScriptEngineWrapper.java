package com.athaydes.rawhttp.reqinedit.js;

import org.openjdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * This class exists only to wrap the Nashorn ScriptEngine class.
 * <p>
 * It was created in order to make it easy to release req-in-edit as a multi-jar library,
 * letting users provide Nashorn as a library since Java 15 when Nashorn was removed from the JDK.
 * <p>
 * This class replaces the standard one in Java 17+ to use the ScriptEngine from the external library
 * from https://github.com/openjdk/nashorn
 */
final class ScriptEngineWrapper {

    private final NashornScriptEngine scriptEngine;

    public ScriptEngineWrapper() {
        scriptEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    }

    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        return scriptEngine.invokeFunction(name, args);
    }

    public Object eval(String script) throws ScriptException {
        return scriptEngine.eval(script);
    }
}
