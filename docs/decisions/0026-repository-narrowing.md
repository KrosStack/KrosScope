# ADR 0026: A search answer is limited to the repository of the current folder

Status: Accepted · Date: 2026-11-22

## In short

- **The unit is the repository that holds the current folder.** When a command finds the index in a parent folder,
  and that index holds several repositories, the search results of `analyze` of a task, `context` and `symbol` come
  from the repository that holds the folder where the command ran. Not from the exact folder, and not from the
  whole workspace.
- **Only search results are limited.** `impact`, `related`, `graph`, the card of `analyze` and `info` still answer
  from the whole index. Links across repositories are what a shared index is for.
- **The line on standard error says so.** It names the repository and how to widen the answer: `--workspace <root>`.
  JSON gets the optional field `narrowedTo`, next to `indexLocation`.
- **A result from outside is never shown without a note.** What `symbol` is given decides: an identity is shown where
  it lies, and a simple name is a search that widens only when the repository has no match.
- **Without a filter nothing changes.** The retrieval code, its queries and its output are as before. A test holds
  the output of the code before this change and compares it.

## Problem

[ADR 0015](0015-resident-service-and-settings.md) planned arguments that narrow one query, and no command line
option, MCP argument or `service` parameter does it. Since a command can find the index of a parent folder, a person
or an agent who works in one repository of a shared workspace asks a question from inside that repository and gets
files and symbols of every repository back. The weights of the ranking are not configurable and the search documents
of a file hold only its path inside its repository, so the folder name cannot steer the search either.

## Options considered

1. **No narrowing.** The line says that the answer covers the whole workspace. It is honest, and it is the answer
   that the current repository does not want.
2. **Filter the ranked result afterwards.** Small, but the pool is the best hits of the whole workspace. When
   another repository matches the words better, the current one can come out empty.
3. **A filter inside the retrieval.** Chosen. Search hits and the nodes that the graph adds to them are dropped when
   they lie outside the repository, and more hits are read until the limits are filled from inside it.
4. **An option to name the repository or a path.** More to learn, and `--workspace` already says where to look. A
   path or module filter can be added later on the same filter.

## Evidence

- `UnnarrowedRetrievalTest` keeps the JSON that `analyze` and `symbol` gave on a workspace of two repositories
  before this change, and asks the index for the same numbers of hits as before when no filter is given.
- `NarrowedRetrievalTest` covers the rules below on the same workspace, among them a repository with more
  than 100 better hits in the other one.
- `NarrowedAnswersTest` and `ServedNarrowedAnswersTest` run the commands in a folder of one repository of a workspace
  of two, in text and JSON, one-shot and through a server. The two answers are equal.
- `NarrowedSymbolAnswersTest` covers the three kinds of match of `symbol`, `SearchCapTest` the cap, and
  `RootRepositoryNarrowingTest` a repository at the workspace root next to a registered one.
- Semantic matches are filtered in `service` and tested with a fake provider. The semantic part itself needs the
  optional components, which a test does not install.

## Decision

- `TaskRetrieval.analyze` and `symbol` take an optional repository. `KrosScope.analyze` and `symbol`, and
  `ContextRequest`, pass it down. `withSemanticRanking` drops the semantic matches of other repositories before it
  fuses them with the lexical ones.
- A search hit is kept when the file that declares it lies in the repository. The nodes that the graph adds to the
  hits are kept by the same rule, and so are the files of an exact mention in the task. The graph is still walked
  across repositories, so a link into another repository carries weight back to this one.
- Hits are read in growing numbers until the limits (50 symbols, 100 candidates and 30 files) are filled from inside
  the repository, or the index has no more. Each repeat asks for four times as many hits, up to 50,000. Without a
  filter the index is asked once, as before.
- What `symbol` is given decides how an exact match is treated.
    - **An identity** is a symbol id, a full qualified name or `path:line`. It names one declaration, so it is shown
      also when it lies outside the repository. The line says in which repository it lies, outside the narrowing, and
      JSON has `outsideNarrowing`. The rest of the answer stays limited.
    - **A simple name** is a search. Declarations of the repository are taken first. Only when no declaration of the
      repository bears the name, or a text matches nothing in it, the search is widened to the whole workspace. The
      line says which repository had nothing, and JSON has `widenedFrom` and no `narrowedTo`. A search of the
      repository that stopped at the cap before any hit is uncertain, not empty, so it is not widened: the answer stays
      limited and empty, with `narrowedTo` and `incomplete`. A name that the
      repository and others declare means the one of the repository, as before.
    - Fuzzy hits inside the repository do not count as a match for a name that only another repository bears. The
      declaration that you typed is what is looked for.
- The command line narrows only when the index came from a parent folder and holds more than one repository, and a
  repository holds the folder (`Repository.directory`, [ADR 0016](0016-multi-repository-workspace.md)). A folder
  under the root that belongs to no repository gets the answer of the whole workspace, and the line says so.
  `--workspace` and an index of the current folder are never narrowed.
- A repository is named as `info` lists it: its directory below the root, or, for a repository at the root, its
  `name`, which is the same name that the note of a chosen declaration uses. The line, `narrowedTo` and the
  documentation use that one name. A repository at the root is only one of several when other repositories are
  registered with `workspace add`.
- The line, for a narrowed answer: `Using the index of a parent folder: <root>/.kscope. Paths are relative to
  <root>. Answer limited to <repository>/; pass --workspace <root> for the whole workspace.` For a repository at the
  root it reads `Answer limited to the repository <name> at the workspace root`. The line of the whole workspace is as
  before. It is printed with the answer, since only the open index knows the repositories.
- `narrowedTo` is the name of the repository, in `analysis`, `context` and `symbol`. It is optional and present only
  for a narrowed answer. `symbol` can also have `outsideNarrowing` and `widenedFrom`, and all three can have
  `incomplete`, each present only when it applies. The schema versions stay.
- A search that reaches the cap of 50,000 candidates before the limits are filled says so: the line says that the
  search stopped at its cap and names `--workspace <root>`, and JSON has `"incomplete": true`. A search never returns fewer results without
  saying why.
- A server runs the same command code with the folder of the client, so served and one-shot answers are equal.

## Consequences

- A task asked in one repository no longer finds files of the others, also when they fit better. `--workspace`
  gives the old answer.
- The cost of a narrowed search is one more index query when the first one holds too few hits of the repository,
  and each query asks for four times as many hits. A repository that matches nothing makes the search read every hit
  of the index, up to the bound of 50,000, in a few queries, and says that its results may be incomplete when the
  index has more. The cost on a large index is not measured.
- The semantic ranking lists every file of the workspace in order. The filter keeps the files of the repository in the
  same order, so the first ten after fusing come from inside it.
- Derived lists of an analysis (tests, boundaries, risks) are not filtered. They are consequences of the hits that
  the search kept, and they follow the links of the chosen files. An impact that crosses into another repository is
  what a user wants to see, so they may name files of other repositories that the chosen files link to.
