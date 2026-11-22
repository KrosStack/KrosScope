# ADR 0024: Diagrams of symbols, drawn only from stored links

Status: Accepted · Date: 2026-11-22

## In short

- **A symbol diagram shows the neighbourhood of one type or one function.** For a type it draws the types that it
  extends, implements, calls, refers to or uses, and the types that do the same to it. For a function it draws the
  calls around it: the callers on the left, the function in the middle, what it calls on the right.
- **It is built only from stored links.** Every line is a relationship that the index holds, drawn with the state and
  the evidence that the index recorded. Nothing is read from source, guessed or inferred for the picture.
- **The boundary is the project graph.** A relationship whose other end is not a symbol of the index is not drawn as a
  node. It is counted. Calls into libraries are not stored, so they are not drawn.
- **The walk is bounded, and what it leaves out is counted.** The diagram says what it did not draw, and why.
- **The map draws it without a layout dependency.** `kscope diagram symbol` prints the same diagram as Mermaid, DOT or
  JSON. The browser map draws the JSON itself, in layers, with a page file of its own.

## Problem

A person who changes a function wants to see who calls it and what it calls. A person who changes a type wants to see
which types depend on it. The text answers of `symbol`, `impact` and `related` list these relationships, but a list
does not show the shape: which neighbours are in the same type or module, which lines are certain, and in what order a
function makes its calls. The graph of a large workspace holds millions of relationships, so a picture that shows
everything is useless and a picture that cuts silently is wrong.

## Options considered

1. **Hand the graph to Graphviz or PlantUML.** Rejected. Both are programs that the tool would have to ship or find,
   with their own licences and install steps, and a diagram that only exists as their output does not follow the
   index in the browser. `graph --format dot` stays as a text export for those who want it.
2. **Walk the whole graph to draw from it.** Rejected. A picture of one symbol needs only its neighbourhood, and a
   walk over every relationship grows with the index, not with the question. The walk asks the graph only about the
   symbols near its start. The command and the map first load the index into memory and draw from there, so a
   diagram does not avoid that load.
3. **Analyse control flow to show branches and loops.** Rejected. The index stores no control flow. Computing it would
   be a second analysis with its own precision, and
   [ADR 0005](0005-evidence-and-resolution-states.md) forbids a picture to claim more than the index established.
4. **A bounded walk over stored links, drawn by the page.** Chosen.

## Evidence

- The diagram module has golden files for the Mermaid, DOT and JSON of a call flow, of a type diagram and of a diagram
  that the budgets cut, and tests that give every relationship that was read exactly one fate: drawn, cut by a budget,
  or counted under one reason.
- Reads go through a counted reader. On an index with about 59,000 callables and 300,000 calls, a function with
  thousands of callers and a class with thousands of members both drew in under two seconds from a cold start and in
  well under a second when warm, and said what they had not read.
- The map tests check that the route answers with the same diagram that the command prints for the same symbol, that
  refusals are errors and not stacks, and that every file of the page is listed in its manifest.

## Decision

- A diagram is drawn for one symbol that is a type or a function. Other symbols are refused with their kind. The walk
  goes both ways from the symbol, one level at a time, up to a depth (1 by default, or as far as the budgets let it),
  and stops at a node cap that follows the limit on lines.
- Budgets bound the work and the picture. The walk makes at most 1,200 reader calls: reads of relationships, reads
  of a type's members, and look-ups of symbols it has not seen. The check that a neighbour is a type reads that
  symbol outside the budget, at most once per symbol that an already read relationship names. A type of more than
  10,000 members
  is not expanded. The command prints at most `--links` lines and `--characters` characters of Mermaid, and the map asks
  for 60 lines by default. A budget never leaves out the chosen symbol.
- Whatever the walk read and did not draw is counted under one reason: cut by a budget, beyond the depth, not expanded,
  of a symbol followed one way only, in no type, to something that is not a function, inside one type, to something
  without a symbol, or stopped by a limit. The counts add up to what was read.
- States are drawn the way the index stores them. Solid is exact. Dashed is probable or a best guess. Dotted goes to a
  reason node, which names why the index could not resolve the relationship or did not analyse it. Inheritance is
  thick. A call carries its position among the calls of its caller. All the relationships of the kinds calls,
  references and type uses between two types share one line, `depends`, which shows their total and keeps the count of
  each kind.
- The route `api/diagram` of the map answers `GET` with the token like the others, on the serve thread. It takes
  `node`, a symbol id of the map, `depth` (1 to 50, or `all`) and `links`, and answers with the diagram as the diagram
  module describes it, whose nodes carry the id of their symbol, and the file node of the map for each of those ids.
  The JSON stays private to the page, as in ADR 0021.
- The page lays a diagram out itself, in layers by distance from the chosen symbol, callers left and callees right.
  Inside a layer the nodes are grouped by their group and sorted by label, so that the same diagram is always drawn
  the same way. The page adds no dependency and no build step, and it asks nothing of the network beyond the local
  server that serves the map.
- Calls into libraries are not stored, so a diagram does not show them; its boundary is the project's own code.

## Consequences

- A diagram is as complete as the index is. A call that the index did not resolve is shown as unresolved, and a call
  that no producer stored is missing. The counts say what was read, not what exists in the source.
- The diagram describes calls in the order of their call sites. It does not show branches, loops, early exits or
  which of two calls runs. A call through an interface, a function value or reflection is drawn to the declaration that
  the index resolved, not to the code that may run. A caller that the walk did not read has no position.
- A node of the diagram carries the id of the symbol that was drawn for it, and the page selects that symbol, so two
  symbols with the same label and group are never mixed up. A node without an id, a reason node, or whose symbol has
  no file in the map can be seen but not selected.
- The layered layout is simple. It does not reduce crossing lines, and a dense diagram is wide. The page scrolls it
  and can widen its panel, and the budgets keep it small.

## Reversal cost

Low. The map side is one route, one package of the service and a few page files. Removing them leaves the command
and the diagram module as they are.
