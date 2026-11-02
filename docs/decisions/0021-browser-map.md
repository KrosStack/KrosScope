# ADR 0021: A read-only map in the browser, served by `serve --map`

Status: Accepted · Date: 2026-10-30

## In short

- **A view, not a tool.** `kscope serve --map` also serves a map of the workspace to a browser: its modules, source
  sets, directories, files, symbols and the relationships between them, kept up to date as the index changes. The map
  only reads the index. It changes nothing, and it asks nothing that a command does not ask.
- **Browser code is accepted for this one surface.** The page is HTML, CSS and JavaScript that ship inside the
  tool, with no dependency, no build step and no network. The code guidelines require an ADR for owned code that is
  not Kotlin, and this is it. The server, the data and every other part stay Kotlin.
- **The JSON is private to the page.** The answers that the page reads are not a public contract. They can change
  between versions without notice, and nothing outside the page is meant to read them.

## Problem

The graph of a large workspace can hold millions of relationships. Text
and JSON answers are right for an agent that asks one question. They do not let a person see how modules, files and
generated code lie against each other, where the unresolved relationships gather, or what a change reaches. A
person who is deciding where a task belongs, or checking what the index knows, needs the whole shape at once and the
detail one click away.

## Options considered

1. **A text or Graphviz export.** `graph --format dot` exists. A graph of this size does not draw usefully in Graphviz,
   and a static file does not follow the index.
2. **A desktop or IDE application.** A new product with its own release, for a view.
3. **A hosted web application.** The index never leaves the machine; this is excluded.
4. **A page served by the tool that is already running.** Chosen. `serve` holds the graph in memory and has the
   token, loopback and single-thread rules that the page needs.
5. **A page built with a framework and a bundler.** Rejected: a toolchain, a lock file and many dependencies to
   keep up for one page, and a build step that Gradle would have to run.

## Evidence

- The status page of the service already serves one page on the loopback address with a random token, only `GET`, a
  checked `Host` header and a strict content security policy. The map reuses that model.
- A server that decodes the graph once and answers many questions is what `serve` is. Everything that touches the
  index runs on its single thread, because the service is not thread-safe; the threads of the map only read an
  immutable copy and hand every question to that thread.
- The tests of the map cover the protections of the address, the answers of every route with and without an index,
  the stream of events with a change by another process and with a scan, and the end of the stream when the server
  stops.

## Decision

- `kscope serve --map` opens a second listener on `127.0.0.1`, at a port that the system picks, beside the one of
  `serve`. It prints `Map: <address>` to standard error when it starts, and `kscope serve --status` prints it too.
  `serve` without the option opens nothing new and behaves as before.
- The address holds a random 256-bit token. Only `GET` is answered, the `Host` header must name `127.0.0.1` or
  `localhost` with the port, and every other path is answered with 404. At most 32 connections are served at a time.
  Every answer forbids caching, content sniffing and referrers, and the policy of the page allows no source but its
  own.
- The files of the page are the resources of the tool. A file is served only when a list in the same directory names
  it; no other name is looked up.
- The server builds an immutable map from the index on the serve thread and hands it to the other threads. Questions
  about impact and about a task run on the serve thread between its other requests, like every use case. Events tell
  the page that the map changed, that a scan runs, and what the watcher says.
- The map is not offered by `kscope service` yet.

## Consequences

- The tool ships JavaScript, CSS and HTML that the Kotlin build does not check. They are read by hand in review, and
  the tests of the server check what the server does with them, not what they do in a browser.
- The service module gains a package for the map. It is a use case of the service like the others, and the page is a
  client of the command line, not of the library.
- A map is built in memory beside the graph. It keeps five numbers of four bytes for each relationship and one table
  entry for each symbol, and it is built only when a page asks for it.
- Because the JSON is not a contract, the page and the server change together and are released together.

## Reversal cost

Low. The map is one package of the command line, one package of the service, one directory of resources and one
option. Removing them leaves `serve` as it was.

## Licence of the packing code

The circle packing in `pack.js` follows the d3-hierarchy implementation, which is under the ISC licence. ADR 0001 lists
Apache-2.0, MIT and BSD; ISC is accepted here because it grants the same rights as MIT in simpler words. Its text ships
as `licenses/d3-hierarchy-ISC.txt`.
