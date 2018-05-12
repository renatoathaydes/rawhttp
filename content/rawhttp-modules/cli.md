---
title: "RawHTTP CLI"
date: 2017-05-11T19:20:11+02:00
draft: true
---

The `rawhttp-cli` module is a CLI (command-line interface) that can send HTTP requests and serve
local files via a RawHTTP server.

## Download

TODO

## Usage

Use `rawhttp send` to send HTTP requests.

Use `rawhttp serve` to serve a local directory via HTTP.

To see the help screen, run `rawhttp help`.

<hr>

### Using the `send` command

The `send` command has the purpose of sending out HTTP requests.
It prints the full HTTP response (including status line, headers, body) to `stdout`.

Options:

* `-f` `--file` <file>
      read request from a file
* `-p` `--print-body`
      print response body only
* `-b` `--body` <file>
      replace message body with contents of a file

#### Send a HTTP request from a file

{{< highlight bash >}}
rawhttp send -f my-request.req
{{< / highlight >}}

Running this command will print the full HTTP response to stdout.

You can send that to another file:

{{< highlight bash >}}
rawhttp send my-request.req > my-response.res
{{< / highlight >}}

#### Send a HTTP request from stdin

{{< highlight bash >}}
rawhttp send "
GET http://example.com/hello
User-Agent: my-client
Accept: text/html"
{{< / highlight >}}

You can also pipe a request from another command:

{{< highlight bash >}}
cat my-request.req | rawhttp send
{{< / highlight >}}

#### Use a file as message body

Assuming a JSON file called `body.json` exists in the working directory:

{{< highlight bash >}}
rawhttp send "
POST http://example.com/hello
Accept: application/json
Content-Type: application/json
" --body body.json
{{< / highlight >}}

<hr>

### Using the `serve` command

The `serve` command starts up a HTTP server to serve the contents of a directory.

Paths may match a file name with or without its extension.
If more than one file exists with the same name but different extensions, the server attempts
to use the request `Accept` header to disambiguate.

Options:

* `-l` `--log-requests`
      log requests received by the server
* `-m` `--media-types` <file>
      use custom Media-Type mappings

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

[Modules](/rawhttp-modules) [HTTP Components](/rawhttp-modules/http-components)
