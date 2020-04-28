{{ define title "RawHTTP" }}
{{ define moduleName "RawHTTP CLI" }}
{{ define path baseURL + "/rawhttp-modules/cli.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

# RawHTTP CLI

The `rawhttp-cli` module is a CLI (command-line interface) that can send HTTP requests and serve
local files via a RawHTTP server.

## Download

```
curl https://jcenter.bintray.com/com/athaydes/rawhttp/rawhttp-cli/1.1.1/rawhttp-cli-1.1.1-all.jar -o rawhttp.jar
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

```bash
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
```

#### Send a HTTP request from a file

```bash
rawhttp send -f my-request.req
```

Running this command will print the full HTTP response to stdout.

You can send the HTTP response to another file:

```bash
rawhttp send my-request.req > my-response.res
```

#### Send a HTTP request from text

```bash
rawhttp send -t "
GET http://example.com/hello
User-Agent: my-client
Accept: text/html"
```

#### Send a HTTP request from stdin

If neither the `-t` nor the `-f` options are used, the request is read from `stdin`.

Just start typing:

```bash
rawhttp send
> GET http://example.com/hello
> User-Agent: my-client
> Accept: text/html
>
```

You can also pipe the request from another command:

```bash
cat my-request.req | rawhttp send
```

#### Use a file as message body

Assuming a JSON file called `body.json` exists in the working directory:

```bash
rawhttp send --body-file body.json -t "
POST http://example.com/hello
Accept: application/json
Content-Type: application/json
"
```

<hr>

### Using the `serve` command

The `serve` command starts up a HTTP server to serve the contents of a directory.

Paths may match a file name with or without its extension.
If more than one file exists with the same name but different extensions, the server attempts
to use the request `Accept` header to disambiguate.

Usage:

`rawhttp serve <dir> [options]`

Options:

```bash
* -l --log-requests
      log requests received by the server
* -m --media-types <file>
      use custom Media-Type mappings
* -p --port <port-number>
      the port to listen on
```

#### Serve files from a local directory

To serve the local directory `public/` on the default port:

```bash
rawhttp serve public/
```

Enable a request logger (prints to stdout):

```bash
rawhttp serve public/ -l
```

Use a different port, say 8082:

```bash
rawhttp serve public/ -p 8082
```

#### Provide custom media-type mapping

The CLI HTTP Server, by default, maps only a few common file extensions to a proper
[Media Type](http://www.iana.org/assignments/media-types/media-types.xhtml).

To override or just provide extra mappings, start the server with a `--media-types` flag:

```bash
rawhttp serve public/ --media-types my-media-types.properties
```

A properties file with mappings should contain entries where the key is a file extension,
 and the value a media-type. It might look something like this:

```properties
sql: application/sql
soap+xml: application/soap+xml
ac3: audio/ac3
3gpp: video/3gpp
mpeg: video/mpeg4-generic
```

Unmapped file extensions result in the `Content-Type` header being set to `application/octet-stream`.

<hr>

{{ include /processed/fragments/_footer.html }}
