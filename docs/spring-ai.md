# Spring AI

`mycelar-spring-ai` turns one `RouteCandidate` into a system block, a tool list, and a `ToolContext` snapshot. The block is the part of the model prompt Mycelar writes. It is rendered after routing, from the path, the capsules on that path, and the capability cone. Spring AI owns `ChatClient` and the model call. The kernel owns the graph.

```text
ChatClient
  → MycelarAdvisor                         order HIGHEST_PRECEDENCE + 250
  → MycelarToolCallingAdvisor              tool loop, reroute after a tool result
  → ChatModel
```

`MycelarAdvisor` must run before the tool-calling advisor. A later order fails at construction with `MycelarConfigurationException`.

The first pass routes the user message and replaces the request's tools with the primary candidate's cone. If the model returns a tool result, `MycelarToolCallingAdvisor` routes again. The intent is the original user text plus that tool result. Tools still come only from `RouteResult.primary()`. Other candidates stay on the result for the caller. They are not merged into the cone.

## Turn

1. Resolve a session id. Read the latest user text.
2. Blank or missing user text: do not route. Expose no Mycelar tools and append no context block.
3. Map a `RouteRequest` and call `runtime.route`.
4. No primary candidate: same as a blank intent.
5. Append the rendered block to the system message. Application system text is kept.
6. Resolve tools for `allowed`, wrap each callback, and replace `toolCallbacks`. Tools already on the request that are outside the cone are dropped.
7. Write the snapshot into `ToolContext`. Keys already present under `mycelar.*` lose to this snapshot.

`top-k` defaults to 1. The adapter only renders and authorizes the primary candidate.

Routing context is the advisor-context entries whose keys start with `mycelar.routing.`. The prefix is stripped. The adapter does not interpret the values. A CRM confirm flag arrives as `mycelar.routing.confirmed` and is read by an application `ConstraintEvaluator` under the key `confirmed`.

Session id, in order: advisor context `mycelar.session.id`, then `ChatMemory` conversation id when that type is on the classpath, then an id generated for this call. It goes to `ToolContext`, not to `RouteRequest`.

## Context block

`MycelarContextRenderer` builds the block from the primary candidate only. Nothing in the block is a free-form prompt template.

| Lines | Source on the candidate |
|---|---|
| `Path` | `path.nodeIds` |
| `Context` | `contextStack` capsule texts, assembler order, one bullet each |
| `Available capabilities` | `capabilityScope.allowed`, sorted by id |
| `Blocked` | `capabilityScope.blocked`: id, decision, sorted constraint ids |

Capsule order is the kernel assembler's order. The renderer does not sort again and does not apply TTL. It copies `ContextCapsule.text` and drops priority, type, confidence, and the source node. Empty sections are omitted. `include-capabilities-in-context` and `include-blocked-in-context` can drop those lists. No primary candidate means no block.

The advisor appends the block to the system message with `augmentSystemMessage`. Existing application system text stays. A previous `<mycelar-context>` block on that message is stripped first, so a reroute replaces it.

```text
<mycelar-context>
Path: crm / leads / message

Context:
- ...

Available capabilities:
- message.draft

Blocked:
- message.send: REQUIRE_CONFIRMATION (confirm-before-send)
</mycelar-context>
```

## Tool bindings

A capability id maps to zero or more `ToolCallback`s. The registry is built from the graph, the tool inventory, programmatic bindings, and YAML.

Inventory is every `ToolCallback` and `ToolCallbackProvider` bean. Two callbacks with the same tool name fail at startup (`DuplicateMycelarToolNameException`).

Name to capability, lowest precedence first (later wins on the same name only where noted):

1. Convention: `CapabilityDescriptor.operationId` equals the tool name. Two descriptors with the same `operationId` fail at startup.
2. `mycelar.spring-ai.tools.mappings` (`toolName` → capability id). An unknown tool name or a blank id fails at startup.
3. `CapabilityToolBindings` beans. The callback may exist only on the binding. The same tool name bound to two capabilities fails at startup.

An allowed capability with no tool is skipped. `strict-tool-bindings=true` throws during the turn, because the cone is not known at startup.

## Guard

Visibility is not authorization. Every exposed callback is a `MycelarGuardedToolCallback`. The call runs only when the snapshot's allowed set contains that capability.

```text
mycelar.path            String              node ids joined with " / "
mycelar.session.id      String
mycelar.capabilities    Set<CapabilityId>
```

Missing keys, nulls, or a wrong type deny the call. `call` without a `ToolContext` denies. Denial is `MycelarCapabilityDeniedException` and the delegate is not invoked. The snapshot is not re-checked against the kernel on each call.

## Autoconfiguration

Registered from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Active when `ChatClient` is on the classpath and `mycelar.spring-ai.enabled` is not false.

A `MycelarGraph` bean is required before the runtime can be created. If the application already exposes `MycelarRuntime`, that bean is used. Otherwise the runtime is built from the graph plus any `ConstraintEvaluator`, `SemanticMatcher`, `RouteScorer`, and `RoutingLimits` beans.

The advisor is added through a `ChatClient` customizer. Do not also register it as a default advisor on an auto-configured builder.

No graph and no runtime means no Mycelar beans.

## Configuration

Prefix `mycelar.spring-ai`.

| Property | Default |
|---|---|
| `enabled` | `true` |
| `advisor-order` | `HIGHEST_PRECEDENCE + 250` |
| `top-k` | `1` |
| `strict-tool-bindings` | `false` |
| `include-capabilities-in-context` | `true` |
| `include-blocked-in-context` | `true` |
| `tools.mappings` | `{}` |

## Failures

| Situation | Outcome |
|---|---|
| Blank intent or no primary route | no tools, the chat continues |
| Allowed capability without a tool | omitted, or a turn failure when strict |
| Blocked capability | absent from tools; listed when the blocked section is enabled |
| Duplicate tool name or `operationId` | startup failure |
| Advisor order not before the tool advisor | startup failure |
| `top-k < 1` | startup failure |
| Bad or missing `ToolContext` | `MycelarCapabilityDeniedException` |
| Kernel `route` rejects the request | the exception propagates |

Routing context values are not logged. They can carry confirmation or tenant data.
