# Embeddings

`mycelar-routing-spring-ai` is an optional `SemanticMatcher`. It scores graph nodes with an application-supplied `EmbeddingModel`. It does not create a `MycelarRuntime`, pick a chat model, or talk to a vector database.

Without an `EmbeddingModel` bean, or with `mycelar.routing.spring-ai.enabled=false`, the kernel keeps its lexical matcher.

The finder still ignores the matcher. Embeddings score only the path set that survived the topology cap, node by node. Capsules are not part of the embedding text.

## What is embedded

`NodeRoutingTextEncoder` reads metadata only: label, description, aliases, keywords. Not the capability descriptor, not attributes, not capsules.

Blank sections are dropped. The rest are joined by a blank line, with no trailing newline. Aliases and keywords keep first-seen order and drop duplicates. Text is not lowercased or stemmed. The label is always present.

```text
Label:
Leads

Description:
Open sales leads.

Keywords:
- follow-up
```

A SHA-256 hex fingerprint of that UTF-8 text detects a changed profile. It is not a security boundary.

The index is an in-memory map from node id to `RouteEmbedding` (vector plus fingerprint). A hit is reused only when the fingerprint still matches. Query vectors are not stored in the index. One query vector is kept on the calling thread for the current intent. User prompts are not cached globally.

## Score

Cosine similarity is `dot / (|a| |b|)`, in `[-1, 1]` when both vectors are non-zero. A zero-length vector or a zero norm scores `0`. Different lengths are a hard `EmbeddingDimensionMismatchException`, including when the failure mode is ignore.

With `embedding.normalize-score=true` (the default), cosine is mapped with `(s + 1) / 2` and clamped to `[0, 1]`. The kernel still clamps the semantic component.

Lazy indexing embeds a node on first use. Eager indexing (`embedding.indexing=eager`) batch-embeds the graph at startup through an `ApplicationRunner`, skipping ids whose fingerprint is unchanged.

## Failure

`ignore-signal` (default) logs a warning and returns `0.5` for the rest of that intent on that thread. Startup with eager indexing still succeeds and may leave the index partial.

`fail-routing` throws `RoutingEmbeddingException` and clears the thread slot so a later `route()` can try again. Eager indexing then fails the application context.

A changed `EmbeddingModel` without clearing the index is undefined. Dimension mismatch is the usual symptom. There is no model fingerprint in this version.

## Autoconfiguration

The matcher is registered when an `EmbeddingModel` is present and the module is enabled. Defaults:

| Bean | |
|---|---|
| `NodeRoutingTextEncoder` | metadata text above |
| `RoutingTextFingerprint` | SHA-256 |
| `VectorSimilarity` | cosine |
| `RoutingEmbeddingIndex` | in-memory |
| `EmbeddingSemanticMatcher` | exposed as `SemanticMatcher` |
| `RoutingEmbeddingInitializer` | only when indexing is eager and a `MycelarGraph` exists |

Several `EmbeddingModel` beans are not disambiguated by name. Mark one `@Primary`, or provide your own `SemanticMatcher`.

`mycelar-spring-ai` already injects an optional `SemanticMatcher` into the runtime. This module only supplies that bean.

## Configuration

Prefix `mycelar.routing.spring-ai`.

| Property | Default |
|---|---|
| `enabled` | `true` |
| `embedding.indexing` | `lazy` |
| `embedding.normalize-score` | `true` |
| `embedding.failure-mode` | `ignore-signal` |

Provider keys and model ids stay in the application's Spring AI configuration.
