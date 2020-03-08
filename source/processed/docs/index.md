{{ define title "RawHTTP" }}
{{ define moduleName "Docs" }}
{{ define path baseURL + "/docs/index.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

<div class="title">RawHTTP Docs</div>

### Core Library:

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
