{{ define title "RawHTTP" }}
{{ define moduleName "ReqInEdit (Jetbrains HTTP files)" }}
{{ define path baseURL + "/rawhttp-modules/req-in-edit.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

# RawHTTP ReqInEdit

The `rawhttp-req-in-edit` module implements a runner for HTTP files written in the [Request In Editor Specification](https://github.com/JetBrains/http-request-in-editor-spec)
format created by JetBrains to make it easier to execute HTTP requests and test their responses.

HTTP requests are written in a way that's very close to the [HTTP RFC-7230](https://tools.ietf.org/html/rfc7230#section-3),
but _with several extensions intended for easier requests composing and editing_.

ReqInEdit supports variables, comments, multiple requests in a single file, environments,
and response handlers which may assert the contents of HTTP responses and set variables for usage in the next
requests.

It is perfect for testing HTTP endpoints.

See the [IntelliJ Documentation](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) for
the full syntax and examples of writing HTTP files. 

> The RawHTTP implementation stays as close as possible to the Jetbrains' specification, but minor variations are unavoidable.
  Please report any differences you may come across.

## Executing HTTP files from the command line

If all you want is to execute HTTP files from the command line or bash scripts, use the
[RawHTTP CLI](cli.html), which uses `rawhttp-req-in-edit` to execute HTTP files.

The rest of this document regards the usage of `rawhttp-req-in-edit` as a Java library, not as a CLI tool. 

## Basic Usage

To execute a HTTP file, parse the file with the `com.athaydes.rawhttp.reqinedit.ReqInEditParser` class, 
then execute it with `com.athaydes.rawhttp.reqinedit.ReqInEditUnit`:

```java
var parser = new ReqInEditParser();

try {
    var entries = parser.parse(new File("my.http"));
    var allSuccess = new ReqInEditUnit().run(entries);
    if (allSuccess) {
        System.out.println("All tests passed");
    } else {
        System.out.println("There were test failures");
    }
} catch (IOException e) {
    // handle error
}
```

## Customizing ReqInEditUnit

The `ReqInEditUnit` class, used to run the parsed entries from a HTTP file as shown above, can be highly customized
to modify all behaviour, including the environment, HTTP parser, HTTP client, file reader, response storage and test reporter.

Here's the full list of parameters in the main constructor:

```java
ReqInEditUnit(HttpEnvironment,
              RawHttp,
              RawHttpClient,
              FileReader,
              ResponseStorage, 
              HttpTestsReporter)
```

The default `HttpEnvironment`, `com.athaydes.rawhttp.reqinedit.js.JsEnvironment`, uses [Nashorn](https://winterbe.com/posts/2014/04/05/java8-nashorn-tutorial/)
to interpret response handlers, and it parses variables using the
JavaScript's [Mustache library](https://mustache.github.io/) (obtained in Java via [webjars](https://www.webjars.org/)).

Notice that the `JsEnvironment` can be created with a constructor pointing to the HTTP project's root dir and with 
an environment name, so that one can use [environments](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html#environment-variables).

The default implementation of `ResponseStorage` writes any response files found in a HTTP file to an actual file
in the working directory.

The following example, when run, will save the HTTP response in the file called `response.json` in the working directory:

```javascript
### GET request with parameter
GET https://httpbin.org/get?show_env=1
Accept: application/json

> {%
client.test("Request executed successfully", function() {
  client.assert(response.status === 200, "Response status is not 200");
});
%}
<> response.json
```

{{ include /processed/fragments/_footer.html }}
