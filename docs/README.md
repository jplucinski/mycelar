# Mycelar

Mycelar routes an intent over a typed application graph and hands the model only the context and tools that sit on the chosen path. The kernel does not call a model, store a graph, or run tools.

| Module | Role |
|---|---|
| [`mycelar-core`](../mycelar-core/) | Graph, routing, capability cone, context capsules |
| [`mycelar-spring-ai`](../mycelar-spring-ai/) | `ChatClient` advisor, tool cone, call guard |
| [`mycelar-routing-spring-ai`](../mycelar-routing-spring-ai/) | Optional `EmbeddingModel` matcher |

The Spring modules depend on the kernel, not on each other. With no embedding bean, scoring stays on the lexical matcher in core.

| Page | |
|---|---|
| [Kernel](kernel.md) | Graph, paths, score, capabilities, capsules |
| [Spring AI](spring-ai.md) | Advisor, tool bindings, autoconfiguration |
| [Embeddings](embeddings.md) | Index, cosine score, failure policy |
| [Twenty sample](example-twenty.md) | Separate CRM application |
