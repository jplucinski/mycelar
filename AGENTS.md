# Working in this repo

Read `docs/` before changing routing, context, or tool authorization. The kernel contract is `docs/kernel.md`. Do not add framework types to `mycelar-core`.

| Doc | What it locks |
|---|---|
| `docs/kernel.md` | Graph, routing, capabilities, context |
| `docs/spring-ai.md` | Advisor, tool cone, autoconfiguration |
| `docs/embeddings.md` | Optional embedding matcher |
| `docs/example-twenty.md` | Separate Twenty CRM sample |

`mycelar-spring-ai` and `mycelar-routing-spring-ai` depend on the kernel, not on each other.
