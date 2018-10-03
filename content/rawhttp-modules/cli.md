---
title: "RawHTTP CLI"
date: 2017-05-11T19:20:11+02:00
draft: false
---

The `rawhttp-cli` module is a CLI (command-line interface) that can send HTTP requests and serve
local files via a RawHTTP server.

## Download

```
curl -sSfL https://jcenter.bintray.com/c/rawhttp/rawhttp-cli/1.0.0/rawhttp-cli-1.0.0.jar -o rawhttp.jar
```

## Usage

Use `rawhttp send` to send HTTP requests.

Use `rawhttp serve` to serve a local directory via HTTP.

To see the help screen, run `rawhttp help`.

<hr>

### Using the `send` command

The `send` command has the purpose of sending out HTTP requests.
It prints the full HTTP response (including status line, headers, body) to `stdout`.

Usage:

```
rawhttp send [options]
```

Options:

{{< highlight shell >}}
* -f --file <file>
      read request from a file
* -t --text <request-text>
      read request as text
* -p --print-body-only
      print response body only
* -l --log-request
      log the request
* -b --body-text <text>
      replace message body with the text
* -g --body-file <text>
      replace message body with the file
{{< / highlight >}}

#### Send a HTTP request from a file

{{< highlight bash >}}
rawhttp send -f my-request.req
{{< / highlight >}}

Running this command will print the full HTTP response to stdout.

You can send the HTTP response to another file:

{{< highlight bash >}}
rawhttp send my-request.req > my-response.res
{{< / highlight >}}

#### Send a HTTP request from text

{{< highlight bash >}}
rawhttp send -t "
GET http://example.com/hello
User-Agent: my-client
Accept: text/html"
{{< / highlight >}}

#### Send a HTTP request from stdin

If neither the `-t` nor the `-f` options are used, the request is read from `stdin`.

Just start typing:

{{< highlight bash >}}
rawhttp send
> GET http://example.com/hello
> User-Agent: my-client
> Accept: text/html
>
{{< / highlight >}}

You can also pipe the request from another command:

{{< highlight bash >}}
cat my-request.req | rawhttp send
{{< / highlight >}}

#### Use a file as message body

Assuming a JSON file called `body.json` exists in the working directory:

{{< highlight bash >}}
rawhttp send --body-file body.json -t "
POST http://example.com/hello
Accept: application/json
Content-Type: application/json
"
{{< / highlight >}}

<hr>

### Using the `serve` command

The `serve` command starts up a HTTP server to serve the contents of a directory.

Paths may match a file name with or without its extension.
If more than one file exists with the same name but different extensions, the server attempts
to use the request `Accept` header to disambiguate.

Usage:

`rawhttp serve <dir> [options]`

Options:

{{< highlight shell >}}
* -l --log-requests
      log requests received by the server
* -m --media-types <file>
      use custom Media-Type mappings
* -p --port <port-number>
      the port to listen on
{{< / highlight >}}

#### Serve files from a local directory

To serve the local directory `public/` on the default port:

{{< highlight bash >}}
rawhttp serve public/
{{< / highlight >}}

Enable a request logger (prints to stdout):

{{< highlight bash >}}
rawhttp serve public/ -l
{{< / highlight >}}

Use a different port, say 8082:

{{< highlight bash >}}
rawhttp serve public/ -p 8082
{{< / highlight >}}

#### Provide custom media-type mapping

The CLI HTTP Server, by default, maps only a few common file extensions to a proper
[Media Type](http://www.iana.org/assignments/media-types/media-types.xhtml).

To override or just provide extra mappings, start the server with a `--media-types` flag:

{{< highlight bash >}}
rawhttp serve public/ --media-types my-media-types.properties
{{< / highlight >}}

A properties file with mappings should contain entries where the key is a file extension,
 and the value a media-type. It might look something like this:

{{< highlight properties >}}
sql: application/sql
soap+xml: application/soap+xml
ac3: audio/ac3
3gpp: video/3gpp
mpeg: video/mpeg4-generic
{{< / highlight >}}

Unmapped file extensions result in the `Content-Type` header being set to `application/octet-stream`.

<hr>

[Modules](/rawhttp/rawhttp-modules) [HTTP Components](/rawhttp/rawhttp-modules/http-components)
