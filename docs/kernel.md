# Kernel

`mycelar-core` is a Java 21 library with no Spring and no model client. An application builds an immutable graph, asks `MycelarRuntime` to route an intent, and reads one `RouteResult`.

An LLM never receives the global capability list. Context capsules come from nodes on the chosen path. Tools come from that path's local cone. Empty paths, an empty cone, and a fully blocked cone are results, not errors.

This is not a workflow engine, a planner, or a knowledge graph. The kernel does not execute capabilities, fetch expansions, persist, or learn weights.

## Layout

```text
io.mycelar.core
  graph/        MycelarGraph, nodes, edges, ids
  routing/      MycelarRuntime, finder, scorer, matcher
  context/      capsules, ContextStack, ContextAssembler
  capability/   descriptors, scope, CapabilityResolver
  constraint/   Constraint, evaluator, decision
  validation/   GraphValidator, GraphValidationException
```

```java
MycelarRuntime runtime = MycelarRuntime.builder()
    .graph(graph)
    .semanticMatcher(matcher)   // default: lexical Jaccard
    .routeScorer(scorer)        // default: composite
    .routingLimits(limits)      // default: depth 8, 100 candidates
    .constraintEvaluators(evaluators)
    .build();

RouteResult result = runtime.route(RouteRequest.builder()
    .intent("draft a follow-up to inactive leads")
    .context(routingContext)
    .topK(3)
    .build());
```

`route()` is pure. There are no checked exceptions and no logging.

## Graph

`MycelarGraph.build()` validates and freezes the graph. A weight change is `withWeights`, which returns a new graph with the same topology.

One node record. No per-domain subclasses.

```text
GraphNode(id, type, metadata, capsules, Optional<CapabilityDescriptor>, Optional<Constraint>)
```

`NodeType` is `DOMAIN`, `RESOURCE`, `CAPABILITY`, `CONTEXT`, or `CONSTRAINT`. A capability node carries a descriptor and nothing else in that slot. A constraint node carries a `Constraint`. Metadata is `label`, `description`, `aliases`, `keywords`, and an opaque attribute map.

```text
GraphEdge(id, source, target, type, weight ∈ [0,1], metadata)
```

| Edge | Ends | Walked by the finder |
|---|---|---|
| `CONTAINS`, `RELATES_TO` | neither end is a constraint | yes |
| `REQUIRES` | capability → constraint | no |
| `EXPANDS_TO` | source is not a constraint | no |

At most one edge per `(source, target, type)`. No self-loops. Constraint nodes have no outgoing edges. When both `CONTAINS` and `RELATES_TO` join the same pair, the finder keeps one hop: higher weight wins, then `CONTAINS`, then `EdgeId`.

Walk-roots are nodes with no incoming walkable edge. A node that is not reachable from that set is also a start, so a pure cycle is not invisible.

`GraphValidationException` carries a list of violations. Invalid graphs do not escape the builder. Cycles, disconnected components, and several roots are valid. Ids of nodes, edges, capabilities, constraints, and capsules are unique. Weights and capsule confidence lie in `[0, 1]`.

## Routing

```text
RouteRequest(intent, RoutingContext, topK)
  → CandidatePathFinder
  → RouteScorer
  → top-K RouteCandidate
       context stack, capability scope, expansion candidates
```

`RoutingContext` is a transient map. It is not written into the graph. A null map becomes empty. A blank intent or `topK < 1` is `IllegalArgumentException`. `topK` above the candidate cap is allowed; the result is shorter.

### Paths

The finder enumerates simple paths (no repeated node) from walk-roots along `CONTAINS` and `RELATES_TO`. Depth is the edge count, from 0 through `maxDepth` (default 8), so a single node is a path. Discovery does not call the semantic matcher.

If there are more paths than `maxCandidates` (default 100):

1. Keep every one-node walk-root.
2. Fill the rest with deeper paths ordered by the geometric mean of edge weights, then by node ids.
3. If there are more roots than the cap, keep the first roots by node id.

### Score

The default matcher tokenizes on non-letters and non-digits, lowercased. A node's tokens are its id split on `.` and `-`, plus label, aliases, keywords, and description. Similarity is Jaccard. An empty side scores 0.

Path semantic score weights later nodes more heavily: node `i` (from 0) has weight `i + 1`.

```text
topologyScore = geometric mean of edge weights, or 0.5 for a one-node path
contextScore  = 0.5 when the routing context is empty
                otherwise the fraction of entries that hit a node attribute
                or appear in some path node's tokens
pathPenalty   = 0.05 * depth / maxDepth

total = 0.45*semantic + 0.35*topology + 0.20*context - pathPenalty
```

Weights are `ScoringWeights` on a `DefaultRouteScorer`. The runtime builder has no separate weights setter; pass the scorer. `RouteScore` keeps `semantic`, `topology`, `runtimeContext`, and `pathPenalty`. Sort is total descending, then node ids. A non-finite custom score is rejected.

The kernel does not merge capsules or capabilities across the candidates after the first.

## Capabilities

The cone is the capability nodes on the path plus capability nodes that are direct `CONTAINS` children of a path node. It is not the subtree. Grandchildren stay out. Off-cone capabilities are omitted, not listed as blocked.

Each in-scope capability collects outgoing `REQUIRES` edges.

| Situation | Decision |
|---|---|
| No constraint | `ALLOW` |
| No registered evaluator for a constraint | `UNRESOLVED` |
| Evaluator returns a decision | that decision |

The worst non-allow wins: `BLOCK`, then `REQUIRE_CONFIRMATION`, then `UNRESOLVED`, then `ALLOW`. Only a full allow puts the capability in `allowed`. Anything else is a `BlockedCapability` on `CapabilityScope`. A missing evaluator fails closed. An evaluator that throws is not rewritten into `UNRESOLVED`. An empty evaluator registry is valid.

The host looks up `CapabilityDescriptor` on the graph. `RouteResult` is not a tool registry. `GatewayId` and `operationId` are strings for the adapter.

## Context

Capsules are taken from path nodes only. A capability can be in scope while the capsules on its own node are absent, because that node was not on the path. The assembled stack is what an adapter renders into the model prompt. `route()` returns the stack. It does not format a prompt.

`ResolvedCapsule` copies the capsule, the source node, and the path index. TTL is copied and not expired. Core has no clock.

Order:

1. Priority descending.
2. Later path node first.
3. Original order on that node.
4. Capsule id.

`CapsuleType` is `INSTRUCTION`, `DOMAIN_CONTEXT`, `CAPABILITY_CONTEXT`, `CONSTRAINT`, or `DYNAMIC_CONTEXT`. That last `CONSTRAINT` is a capsule kind, not a node type. Capsules stay short.

## Expansion

Every `EXPANDS_TO` edge leaving a path node becomes an `ExpansionCandidate`. The same target from two path nodes is two candidates. `ContextExpander` is a port for adapters. `MycelarRuntime.route()` does not call it. Core performs no HTTP, database, or vector access.

## Errors

| Situation | Outcome |
|---|---|
| Invalid graph or weights | `GraphValidationException` |
| Null graph in the builder | `IllegalArgumentException` |
| Blank intent, `topK < 1` | `IllegalArgumentException` |
| Null matcher, scorer, or limits | defaults |
| No paths, empty cone, all blocked | a result, no throw |
| Evaluator throws | propagates |
| Non-finite custom matcher or scorer | `IllegalArgumentException` |
