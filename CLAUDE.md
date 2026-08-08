# CLAUDE.md

Guidance for Claude Code (and any other AI agent) working in this repo.

## IMPORTANT — Keep this file current
Every code change made in this repo must also update this CLAUDE.md if the
change affects architecture, build/run steps, tech stack, or project
structure. Treat this file as living documentation, not a one-time snapshot.

## Project
AI-powered PR reviewer: GitHub webhook → Spring Boot API → Amazon SQS
queue → AWS Lambda worker → LLM review → posts comments back to the PR.

## Structure
- `spring-boot-api/` — webhook receiver, persistence (PostgreSQL), review query API, SQS publisher
- `aws-lambda/` — SQS-triggered Lambda worker handler: fetch diff, call OpenAI API, post review, callback to API
- `docker-compose.yml` — local Postgres for the API
- `helm/ai-pr-reviewer/` — Helm chart (api Deployment, optional Bitnami `postgresql` subchart, Secret/ConfigMap, Ingress, HPA)
- `k8s/` — plain k8s manifests (non-Helm alternative)

## Build
- Maven, two independent modules (no parent aggregator, no wrapper)
- Java 21 (both modules — `spring-boot-api/pom.xml` `java.version`, `aws-lambda/pom.xml` `maven.compiler.source/target`)

```bash
cd spring-boot-api && mvn spring-boot:run
cd aws-lambda && mvn clean package
```

### Serverless Local Testing (AWS SAM)
```bash
cd aws-lambda
sam local invoke PRReviewerLambdaFunction -e events/sqs_event.json
```

### Containers
```bash
docker build -t ai-pr-reviewer-api:1.0.0 ./spring-boot-api
docker build -t ai-pr-reviewer-lambda:1.0.0 ./aws-lambda
```

### Kubernetes / Helm
Chart lives at `helm/ai-pr-reviewer/`. `postgresql.enabled` defaults to
`false` (production-safe) — supply `database.external.*` + `secrets.dbPassword`
for an external Postgres, or set `postgresql.enabled=true` for a dev-only
in-cluster instance. Secrets (`GITHUB_WEBHOOK_SECRET`, `GITHUB_API_TOKEN`,
`DB_PASSWORD`, `AWS_SQS_QUEUE_URL`, `OPENAI_API_KEY`) are
never committed — copy `values-secret.yaml.example` to a gitignored
`values-secret.yaml`.

```bash
helm dependency build helm/ai-pr-reviewer
helm install ai-pr-reviewer helm/ai-pr-reviewer -f helm/ai-pr-reviewer/values-secret.yaml
```

Or apply the plain manifests directly (see `k8s/secret.yaml.example`):
```bash
kubectl apply -f k8s/
```

## Tech stack
| Layer | Tech |
|---|---|
| API | Spring Boot 3, Spring Data JPA, PostgreSQL, Actuator (health probes) |
| Messaging | Amazon SQS |
| Compute | AWS Lambda (Java 21 RequestHandler event-driven worker) |
| Orchestration | AWS SAM (`aws-lambda/template.yaml`) / Kubernetes Helm chart (`helm/`) |
| AI | OpenAI API (chat completions, JSON-mode) |
| External API | GitHub REST API |

See `README.md` for full setup/prerequisites and API reference.
