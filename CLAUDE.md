# CLAUDE.md

Guidance for Claude Code (and any other AI agent) working in this repo.

## IMPORTANT — Keep this file current
Every code change made in this repo must also update this CLAUDE.md if the
change affects architecture, build/run steps, tech stack, or project
structure. Treat this file as living documentation, not a one-time snapshot.

## Project
AI-powered PR reviewer: GitHub webhook → Spring Boot API → Azure Service Bus
queue → Azure Function → LLM review → posts comments back to the PR.

## Structure
- `spring-boot-api/` — webhook receiver, persistence (PostgreSQL), review query API
- `azure-function/` — queue-triggered function: fetch diff, call Azure OpenAI, post review
- `docker-compose.yml` — local Postgres for the API

## Build
- Maven, two independent modules (no parent aggregator, no wrapper)
- Java 21 (both modules — `spring-boot-api/pom.xml` `java.version`,
  `azure-function/pom.xml` `maven.compiler.source/target` and the
  `azure-functions-maven-plugin` runtime `javaVersion`)

```bash
cd spring-boot-api && mvn spring-boot:run
cd azure-function && mvn clean package && mvn azure-functions:run
```

## Tech stack
| Layer | Tech |
|---|---|
| API | Spring Boot 3, Spring Data JPA, PostgreSQL |
| Messaging | Azure Service Bus |
| Compute | Azure Functions (Java, queue trigger) |
| AI | Azure OpenAI (chat completions, JSON-mode) |
| External API | GitHub REST API |

See `README.md` for full setup/prerequisites and API reference.
