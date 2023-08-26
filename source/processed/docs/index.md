{{ define title "RawHTTP" }}
{{ define moduleName "Docs" }}
{{ define path baseURL + "/docs/index.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

<div class="title">RawHTTP Docs</div>

### Core Library:

[![RawHTTP Core Javadocs](https://javadoc.io/badge2/com.athaydes.rawhttp/rawhttp-core/javadoc.svg)](https://javadoc.io/doc/com.athaydes.rawhttp/rawhttp-core)

{{ for section /processed/docs }}
{{ if section.moduleName != moduleName }}
* [{{eval section.moduleName}}]({{ eval section.path }})
{{ end }}
{{ end }}

### Modules:

{{ for module /processed/rawhttp-modules }}
* [{{eval module.moduleName}}]({{ eval module.path }})
{{ end }}

{{ include /processed/fragments/_footer.html }}
