# Query normalization

Retrieval and the English-stop-word BM25 baseline use the same fixed 33-word common English list as
[Apache Lucene's EnglishAnalyzer](https://github.com/apache/lucene/blob/main/lucene/analysis/common/src/java/org/apache/lucene/analysis/en/EnglishAnalyzer.java).
The list is declared in `EnglishStopWords` and is independent of repository tasks. No ticket, product,
workflow or task-specific words are removed. Terms such as `app`, `users`, `want` and `update` remain.

Retrieval applies the shared code tokenizer, removes these stop words, and lets the SQLite index apply
Porter stemming. Raw BM25 uses ordinary word tokens; its English variant removes the same stop words
from both documents and queries before stemming. The lexical retrieval baseline uses the same code
tokenizer, symbol fields and path boosts as full retrieval.

Artifact hints use the independently documented [capability vocabulary](retrieval-vocabulary.md).
They match literal, contiguous token sequences and never inferred user-interface phrases.
