{{ define title "RawHTTP" }}
{{ define moduleName "Get Started" }}
{{ define path baseURL + "/docs/get-started.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

## Add a dependency on RawHTTP

### Maven

```xml
<dependency>
    <groupId>com.athaydes.rawhttp</groupId>
    <artifactId>rawhttp-core</artifactId>
    <version>{{ eval coreVersion }}</version>
</dependency>
```

### Gradle

```groovy
dependency 'com.athaydes.rawhttp:rawhttp-core:{{ eval coreVersion }}'
```

### RawHTTP CLI

Please check out the [CLI Documentation]({{eval baseURL }}/rawhttp-modules/cli.html) if you want to use RawHTTP as
a CLI utility to run HTTP servers and clients. It includes support for running [HTTP files](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html),
which is basically the equivalent of a [PostMan](https://www.postman.com/) on the terminal.

<hr>

{{ include /processed/fragments/_footer.html }}
