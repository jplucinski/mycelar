# Mycelar

Graph-based context runtime for LLM applications.

## Modules

| Module | Role |
|--------|------|
| [`mycelar-core`](mycelar-core/) | Kernel: graph, routing, context, capabilities |
| [`mycelar-spring-ai`](mycelar-spring-ai/) | `MycelarAdvisor`, tool scoping, `ToolContext` authorization |
| [`mycelar-routing-spring-ai`](mycelar-routing-spring-ai/) | Optional `EmbeddingModel` semantic matcher |

`mycelar-spring-ai` and `mycelar-routing-spring-ai` both depend on core, not on each other.

## Requirements

- Java 21
- Gradle 8.x (wrapper included)
- Spring Boot 4.0 / Spring AI 2.0 for the Spring modules
- Own `ChatModel` / `EmbeddingModel` beans (no provider starter is pulled in)

## Build

```bash
./gradlew test
```

```bash
./gradlew :mycelar-core:test
./gradlew :mycelar-spring-ai:test
./gradlew :mycelar-routing-spring-ai:test
```

## Spring Boot

```kotlin
dependencies {
    implementation(project(":mycelar-spring-ai"))
    implementation(project(":mycelar-routing-spring-ai")) // optional
    implementation("org.springframework.ai:spring-ai-starter-model-openai") // example provider
}
```

A `MycelarGraph` bean is required. Auto-configuration then registers `MycelarRuntime`, attaches `MycelarAdvisor` to the `ChatClient` builder, and registers `EmbeddingSemanticMatcher` when an `EmbeddingModel` bean exists.

Per-request routing context: `ChatClient` keys prefixed `mycelar.routing.`.

## Configuration

### `mycelar.spring-ai`

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Toggle Mycelar advisor auto-configuration |
| `top-k` | `1` | Number of route candidates to consider |
| `advisor-order` | `HIGHEST_PRECEDENCE + 250` | Must run before `ToolCallingAdvisor` |
| `strict-tool-bindings` | `false` | Fail when an allowed capability has no bound tool |
| `include-capabilities-in-context` | `true` | List allowed capabilities in the system block |
| `include-blocked-in-context` | `true` | List blocked capabilities in the system block |
| `tools.mappings` | `{}` | Map tool name → capability id (YAML binding) |

### `mycelar.routing.spring-ai`

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Toggle embedding matcher auto-configuration |
| `embedding.indexing` | `lazy` | `lazy` embed on first route, `eager` batch at startup |
| `embedding.normalize-score` | `true` | Map cosine similarity to `[0, 1]` |
| `embedding.failure-mode` | `ignore-signal` | `ignore-signal` (neutral score) or `fail-routing` |

## Docs

| Doc | |
|---|---|
| [Kernel](docs/kernel.md) | Graph, routing, capabilities, context |
| [Spring AI](docs/spring-ai.md) | Advisor, tool cone, autoconfiguration |
| [Embeddings](docs/embeddings.md) | Optional semantic matcher |
