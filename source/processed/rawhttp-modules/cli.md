{{ define title "RawHTTP" }}
{{ define moduleName "RawHTTP CLI" }}
{{ define path baseURL + "/rawhttp-modules/cli.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

# RawHTTP CLI

[![Javadocs](https://javadoc.io/badge2/com.athaydes.rawhttp/rawhttp-cli/javadoc.svg)](https://javadoc.io/doc/com.athaydes.rawhttp/rawhttp-cli)

The `rawhttp-cli` module is a CLI (command-line interface) that can send HTTP requests and serve
local files via a RawHTTP server.

## Download

To run on Java 8 through 14:

```curl
curl -sSfL https://repo1.maven.org/maven2/com/athaydes/rawhttp/rawhttp-cli/{{ eval cliVersion }}/rawhttp-cli-{{ eval cliVersion }}-all.jar -o rawhttp-all.jar
```

To run on Java 17+:

> A different jar is needed because Nashorn is not included in the JDK anymore, so it must be included in the jar:

```
curl -sSfL https://repo1.maven.org/maven2/com/athaydes/rawhttp/rawhttp-cli/{{ eval cliVersion }}/rawhttp-cli-{{ eval cliVersion }}-jdk17.jar -o rawhttp-jdk17.jar
```

## Usage

Before we start, if your shell supports `alias`, create an alias for the command to run the RawHTTP CLI:

```bash
alias rawhttp="java -jar $(pwd)/rawhttp.jar"
```

Now, running the RawHTTP CLI is as easy as typing `rawhttp`.

The following sub-commands are supported:

* `send`  - sends a HTTP request from sysin, a file, or an argument.
* `run`   - runs a HTTP file in the [JetBrains ReqInEdit](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) format.
* `serve` - starts a HTTP server to serve the contents of a directory.
* `help`  - show help information.

To see information about a particular sub-command, `send` for example:

```bash
rawhttp help send
```

<hr>

### Using the `send` command

The `send` command has the purpose of sending out single HTTP requests.

By default, it prints the full HTTP response (including status line, headers, body) to `stdout`, but it can also
print only parts of the response, and statistics about the request.

Usage:

```bash
rawhttp send [options]
```

Options:

```bash
  * -f --file <file>
      read request from a file
  * -t --text <request-text>
      read request as text
  * -i --ignore-tls-cert
      ignore TLS certificate when connecting to servers.
  * -p --print-response-mode <option>
      option is one of: response|all|body|status|stats
        - response: (default) print the full response
        - all: print the full response and statistics about the request
        - body: print the response body
        - status: print the response status-line
        - stats: print statistics about the request
  * -l --log-request
      log the request
  * -b --body-text <text>
      replace message body with the text
  * -g --body-file <file>
      replace message body with the contents of the file
```

Statistics include the following information:

 * `Connect time`: the time it took to connect to the server (includes only the Socket::connect call).
 * `First received byte time (FRBT)`: the time between the first byte of the request being sent, and the first byte of the response being received.
 * `Total response time (TRT)`: time to receive the first byte, plus the time to download the full response.
 * `Bytes received`: the number of bytes received from the server.
 * `Throughput`: the total number of bytes received, divided by (TRT - FRBT) in seconds.

If no `-f` or `-t` options are given, a HTTP request is read from stdin.

#### Send a HTTP request from a file

```bash
rawhttp send -f my-request.req
```

Running this command will print the full HTTP response to stdout.

You can send the HTTP response to another file:

```bash
rawhttp send my-request.req > my-response.res
```

Or print statistics about it:

```bash
rawhttp send my-request.req -p stats

Connect time: 179.35 ms
First received byte time: 698.79 ms
Total response time: 713.03 ms
Bytes received: 464
Throughput (bytes/sec): 32572
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

### Using the `run` command

The `run` sub-command executes an HTTP file as defined by [Jetbrains](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html).

Usage:

```bash
rawhttp run <http-file> [options]
```

Options:

```bash
  * -e --environment <name>
      the name of the environment to use
  * -c --cookiejar <file>
      the file to use as a cookie jar
  * -i --ignore-tls-cert
      ignore TLS certificate when connecting to servers.
  * -p --print-response-mode
      one of: response|all|body|status|stats
        - response: (default) print the full responses
        - all: print the full response and statistics about each request
        - body: print the response bodies
        - status: print the response status-lines
        - stats: print statistics about each request
  * -l --log-request
      log the request
```

The `stats` argument of the `print-response-mode` option works as for the `send` command.

#### Example HTTP file

The following shows an example HTTP file that sends a GET request, then a POST request:

```javascript
# This is a HTTP file in the format specified in https://github.com/JetBrains/http-request-in-editor-spec
#
# You can run this file in IntelliJ IDEA, or using the rawhttp-cli.

### GET request with parameter
GET https://httpbin.org/get?show_env=1
Accept: application/json

> {%
client.test("Request executed successfully", function() {
  client.assert(response.status === 200, "Response status is not 200");
});
%}
<> response.json

### Send POST request with body as parameters
POST https://httpbin.org/post
Content-Type: application/x-www-form-urlencoded
X-Content-Type-Options: nosniff

id=999&value=content

> {%
client.test("Request executed successfully", function() {
  client.assert(response.status === 200, "Response status is not 200");
  client.assert(response.contentType.mimeType === 'application/json', "Not JSON: " + response.contentType);
  // validate the body
  var json = response.body;
  client.assert(json.form.id === '999', "Unexpected JSON: " + json);
  client.assert(json.form.value === 'content', "Unexpected JSON: " + json);
  client.assert(json.headers['X-Content-Type-Options'] === 'nosniff', "Unexpected JSON: " + json);
});
%}
```

To run this file, say it's called `test.http`:

```bash
rawhttp run test.http
```

#### Dynamic variables

Dynamic variables generate a value each time you run a request:

* `$uuid`: generates a universally unique identifier (UUID-v4)
* `$timestamp`: generates the current UNIX timestamp
* `$randomInt`: generates a random integer between 0 and 1000.

For example:

```http
GET http://localhost/api/get?id=\{{$uuid}}
```

#### Environments

An environment is a JSON file which defines variables that can be used in HTTP files.

The file must be named `http-client.env.json`, and the `http-client.private.env.json` file holds the
sensitive authorization data.

The top-level keys in the JSON object are the names of the environments. For example, the following file defines
two environments, `development` and `production`:

```json
{
    "development": {
        "host": "localhost",
        "id-value": 12345,
        "username": "joe",
        "password": "123",
        "my-var": "my-dev-value"
    },

    "production": {
        "host": "example.com",
        "id-value": 6789,
        "username": "bob",
        "password": "345",
        "my-var": "my-prod-value"
    }
}
```

The following request could be used with this environment:

```http
GET http://\{{host}}/api/json/get?id=\{{id-value}}
Authorization: Basic \{{username}} \{{password}}
Content-Type: application/json

{
"key": \{{my-var}}
}
```

To run that with the `development` environment:

```bash
rawhttp run req.http -e development
```

<hr>

### Using the `serve` command

The `serve` command starts up a HTTP server to serve the contents of a directory.

Paths may match a file name with or without its extension.
If more than one file exists with the same name but different extensions, the server attempts
to use the request `Accept` header to disambiguate.

Usage:

```
rawhttp serve <dir> [options]
```

Options:

```bash
  * -l --log-requests
      log requests received by the server
  * -m --media-types <file>
      use custom Media-Type mappings
  * -p --port <port-number>
      the port to listen on
  * -k --keystore
      the keystore to use for TLS connections.
  * -w --keystore-password
      the keystore password. Ignored if keystore not given.
  * -r --root-path <path>
      the path to use as the root path (not incl. in file path, only URL)
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

Use a different port, say 8082, and accept only HTTPS by providing a keystore containing a TLS certificate:

```bash
rawhttp serve public/ -k mykeystore.jks -w password -p 8082
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
