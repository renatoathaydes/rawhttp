var System = Java.type("java.lang.System");
var UUID = Java.type("java.util.UUID");
var Random = Java.type("java.util.Random");
var ArrayList = Java.type("java.util.ArrayList");

var RANDOM = new Random();

function __emptyView__() {
    return {
        $timestamp: function() {
            return System.currentTimeMillis();
        },
        $uuid: function() {
            return UUID.randomUUID().toString();
        },
        $randomInt: function() {
            return RANDOM.nextInt(1001);
        }
    };
}

var client = {
    __view__: __emptyView__(),
    __tests__: [],
    test: function(name, check) {
        client.__tests__.push({name: name, check: check});
    },
    assert: function(cond, msg) {
        if (!cond) {
            throw msg || "assertion failed";
        }
    },
    log: function(msg) {
        print(msg);
    }
};

client.global = {
    get: function(name) {
        return client.__view__[name];
    },
    set: function(name, value) {
        client.__view__[name] = value;
    },
    isEmpty: function() {
        for (var key in client.__view__) {
            if (key !== "$uuid" && key !== "$randomInt" && key !== "$timestamp") {
                return false;
            }
        }
        return true;
    },
    clear: function(name) {
        delete client.__view__[name];
    },
    clearAll: function() {
        client.__view__ = __emptyView__();
    }
};

function __loadEnvironment__(envName, jsonEnv, privateJsonEnv) {
    var env = JSON.parse(jsonEnv)
    var privEnv = JSON.parse(privateJsonEnv)
    if (typeof env[envName] === 'object') {
        for (var k in env[envName]) {
            client.__view__[k] = env[envName][k];
        }
    }
    if (typeof privEnv[envName] === 'object') {
        for (var k in privEnv[envName]) {
            client.__view__[k] = privEnv[envName][k];
        }
    }
}

function __runAllTests__() {
    var tests = client.__tests__;
    var errors = new ArrayList();
    for each (var test in tests) {
        try {
            test.check();
        } catch (e) {
            errors.add("Test failed: " + test.name + " (" + e + ")");
        }
    }
    return errors;
}

function __mustacheRender__(template) {
    return Mustache.render(template, client.__view__);
}
