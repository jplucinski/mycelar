# Twenty sample

`mycelar-example-twenty` is a separate Spring Boot application. It is not a module of this build. It shows the kernel on [Twenty](https://github.com/twentyhq/twenty), an open-source CRM: a real graph, a confirm constraint, and `@Tool` methods over Twenty's REST API.

The sample brings its own Twenty instance and its own `ChatModel`. This repository does not embed Twenty, and the sample does not depend on the embedding module.

```text
POST /api/chat
  → ChatClient
  → MycelarAdvisor
  → tool loop
  → Twenty tools
  → Twenty REST
```

## Graph

A domain node `twenty` contains four resources: `people`, `companies`, `opportunities`, `notes`. Each resource contains capabilities whose `operationId` is the Spring AI tool name.

| Tools | HTTP |
|---|---|
| `searchPeople`, `getPerson`, `createPerson`, `updatePerson` | `/rest/people` |
| `searchCompanies`, `getCompany`, `createCompany`, `updateCompany` | `/rest/companies` |
| `searchOpportunities`, `getOpportunity`, `createOpportunity`, `updateOpportunity` | `/rest/opportunities` |
| `searchNotes`, `getNote`, `createNote` | `/rest/notes` |

There are no delete tools. `createNote` requires `confirm-before-send`. The evaluator allows the capability only when the routing context has `confirmed=true`. Otherwise the decision is `REQUIRE_CONFIRMATION` and the tool stays out of the cone.

`people` and `notes` also have `EXPANDS_TO` edges so the context block has local text. The sample does not run a `ContextExpander`.

Every descriptor uses the gateway id `twenty-rest`.

## HTTP

`TWENTY_BASE_URL` defaults to `https://api.twenty.com`. The client calls `{baseUrl}/rest` with `Authorization: Bearer`. `TWENTY_API_KEY` is required at runtime. Tests use a dummy key and a mock server.

List is `GET /{plural}` with `limit` and an optional filter. Get, create, and update are `GET`, `POST`, and `PATCH` on `/{plural}/{id}`. After `createNote`, a target id produces `POST /noteTargets` with the note id and exactly one of person, company, or opportunity.

Tools return the response body to the model. A non-2xx status is an unchecked exception from the client.

## Chat

`POST /api/chat`

```json
{ "message": "Find people named Ada", "confirmed": false }
```

`message` is the user text and therefore the routing intent. `confirmed` is set on the advisor context as `mycelar.routing.confirmed`. The response is `{ "content": "<assistant text>" }`.

The running app uses `spring-ai-starter-model-openai`. Tests replace `ChatModel` and do not need a provider key.

The Gradle build includes this repo with `includeBuild("../mycelar")` and depends on `io.mycelar:mycelar-spring-ai`. It does not copy kernel sources.

## Tests

No live Twenty and no live model.

- The HTTP client against a mock server.
- `MycelarRuntime.route` on the sample graph: intents land on the expected resource, and `createNote` stays blocked until `confirmed` is set.
- A Spring test whose fake model asks for a people tool, and which checks that `createNote` is absent from the cone when `confirmed` is false.
