{{ define title "RawHTTP" }}
{{ define moduleName "RawHTTP Http-Components Integration" }}
{{ define path baseURL + "/rawhttp-modules/http-components.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

# RawHTTP http-components

RawHTTP can be integrated with the popular
[HttpComponents library's](https://hc.apache.org/httpcomponents-client-4.5.x/) HTTP client.

This can be useful if you need to use features from a more complete HTTP client, such as cookie
administration and automatic redirects, but prefer to use the simpler API provided by RawHTTP.

### Sending a HTTP Request

<hr>

{{ include /processed/fragments/_footer.html }}
