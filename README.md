# AI-Powered PR Reviewer

An event-driven code review assistant that automatically reviews GitHub pull requests using an LLM, and posts inline comments back on the PR — flagging security issues, bugs, and maintainability concerns before a human reviewer even opens the diff.

## Why this project

Every engineering team deals with review latency and inconsistent review quality. This automates the first pass: catching obvious issues (SQL string concatenation, missing null checks, N+1 queries) immediately on push, so human reviewers can focus on design and business logic.

## Architecture

```
GitHub PR opened/updated
        │  webhook (HMAC-signed)
        ▼
Spring Boot API  ──────────────► persists event, publishes to queue
        │                              │
        │                     Azure Service Bus Queue
        │                              │
        │                              ▼
        │                     Azure Function (queue trigger)
        │                        1. fetch PR diff (GitHub API)
        │                        2. send diff to Azure OpenAI
        │                        3. parse structured JSON response
        │                        4. post review comments (GitHub API)
        ◄──────────────────────  5. POST result back to Spring Boot API
        ▼
   review persisted, viewable via GET /api/reviews
```

**Why split the work this way:** the webhook handler needs to respond to GitHub in under 10 seconds or the delivery is marked failed. The actual review (diff fetch + LLM call) can take much longer and shouldn't block that response — so it's handed off to an async, independently-scalable Azure Function via a queue. This is a standard "sync API + async event-driven compute" pattern used for any slow, bursty workload.

## Tech stack

| Layer | Tech |
|---|---|
| API | Spring Boot 3, Spring Data JPA, PostgreSQL |
| Messaging | Azure Service Bus |
| Compute | Azure Functions (Java, queue trigger) |
| AI | Azure OpenAI (chat completions, JSON-mode) |
| External API | GitHub REST API (diff fetch, review/comment posting) |

## Project structure

```
ai-pr-reviewer/
├── spring-boot-api/     # Webhook receiver, persistence, review query API
├── azure-function/      # Diff fetch + LLM review + comment posting
├── docker-compose.yml   # Local Postgres for the API
└── docs/                # Architecture diagram, demo
```

## Running locally — step by step

### Prerequisites
- Java 21+, Maven
- Docker (for local Postgres)
- [ngrok](https://ngrok.com) (or similar) to expose your local API to GitHub's webhook delivery
- An Azure subscription (Service Bus namespace + Function App + Azure OpenAI resource) — needed for the full end-to-end flow, not for just running the API
- A GitHub repo you can add a webhook to, and a personal access token with `repo` scope

### Getting your credentials

Every value below is required somewhere in steps 1-4. What it is, why the project needs it, and where to get it:

| Credential | What it is | Why needed | Where to get it |
|---|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | A shared secret string you invent yourself — not issued by GitHub | Spring Boot API uses it to verify the HMAC signature on incoming webhook payloads, so only real GitHub deliveries (not a random POST from anyone) trigger a review | Make one up yourself, e.g. `openssl rand -hex 20`. Same string goes into the API env var **and** the GitHub webhook's "Secret" field |
| `GITHUB_API_TOKEN` | GitHub personal access token | Both the API and the Azure Function call GitHub's REST API (fetch PR diff, post review comments) — that needs authentication | GitHub → click your profile picture → **Settings** → **Developer settings** (bottom of left sidebar) → **Personal access tokens** → **Tokens (classic)** → **Generate new token (classic)** → check the `repo` scope → **Generate token** → copy it immediately, it's shown only once. Direct link: https://github.com/settings/tokens |
| ngrok URL | A public HTTPS URL that tunnels to your local `localhost:8080` | GitHub's webhook delivery needs a public internet address to POST to — it can't reach your laptop's `localhost` directly | Install ngrok, run `ngrok config add-authtoken <token>` once (token from https://dashboard.ngrok.com/get-started/your-authtoken after free signup at https://dashboard.ngrok.com/signup), then run `ngrok http 8080` — the `https://<random>.ngrok-free.app` line it prints (also visible at `http://127.0.0.1:4040`) is your URL. It changes every time you restart ngrok unless you're on a paid plan with a reserved domain |
| `AZURE_SERVICEBUS_CONNECTION_STRING` | Connection string for an Azure Service Bus namespace + queue | The API publishes each webhook event onto this queue; the Azure Function consumes from it — this is the async hand-off described in [Architecture](#architecture) | Azure CLI — see [Create Service Bus via CLI](#create-service-bus-via-cli) below |
| `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`, `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_API_VERSION` | Azure OpenAI resource details | The Azure Function sends the PR diff to this deployment to generate the structured JSON review | Azure CLI — see [Create Azure OpenAI via CLI](#create-azure-openai-via-cli) below |
| `SPRING_API_CALLBACK_URL` | URL the Azure Function POSTs its finished review back to | Closes the loop — persists the review result and makes it queryable via `GET /api/reviews/{eventId}` | Not fetched from anywhere — for local runs it's just `http://localhost:8080/api/reviews/callback` (the Spring Boot API running on your machine) |

#### Create Service Bus via CLI

```bash
az login

az group create \
  --name ai-pr-reviewer-rg \
  --location eastus

# namespace name must be globally unique
az servicebus namespace create \
  --resource-group ai-pr-reviewer-rg \
  --name ai-pr-reviewer-sb-<yourinitials> \
  --location eastus \
  --sku Basic

az servicebus queue create \
  --resource-group ai-pr-reviewer-rg \
  --namespace-name ai-pr-reviewer-sb-<yourinitials> \
  --name pr-review-requests

# this is the value for AZURE_SERVICEBUS_CONNECTION_STRING
az servicebus namespace authorization-rule keys list \
  --resource-group ai-pr-reviewer-rg \
  --namespace-name ai-pr-reviewer-sb-<yourinitials> \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString \
  --output tsv
```

Queue name must match `AZURE_SERVICEBUS_QUEUE_NAME` (defaults to `pr-review-requests` — leave that env var unset to use the default).

#### Create Azure OpenAI via CLI

Requires your subscription to have Azure OpenAI access approved (request at https://aka.ms/oai/access if `az cognitiveservices account create` below errors with an access-denied message).

```bash
# resource name must be globally unique. Run once — fails if it already exists.
az cognitiveservices account create \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --resource-group ai-pr-reviewer-rg \
  --kind OpenAI \
  --sku S0 \
  --location eastus \
  --yes

# deploy a model — this is AZURE_OPENAI_DEPLOYMENT
# fails if the deployment already exists, or if your subscription lacks quota
az cognitiveservices account deployment create \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --resource-group ai-pr-reviewer-rg \
  --deployment-name gpt-5-mini \
  --model-name gpt-5-mini \
  --model-version "2025-08-07" \
  --model-format OpenAI \
  --sku-name GlobalStandard \
  --sku-capacity 50

# this is AZURE_OPENAI_ENDPOINT
az cognitiveservices account show \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --resource-group ai-pr-reviewer-rg \
  --query properties.endpoint \
  --output tsv

# this is AZURE_OPENAI_API_KEY
az cognitiveservices account keys list \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --resource-group ai-pr-reviewer-rg \
  --query key1 \
  --output tsv

# verify the deployment exists — name only
az cognitiveservices account deployment list \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --resource-group ai-pr-reviewer-rg \
  --query "[].name" \
  --output tsv

# or full detail (name, model, sku, ...) as a table
az cognitiveservices account deployment list \
  --resource-group ai-pr-reviewer-rg \
  --name ai-pr-reviewer-openai-<yourinitials> \
  --output table
```

`AZURE_OPENAI_API_VERSION` isn't fetched via CLI — use whatever API version matches your model, e.g. `2024-06-01` (check https://learn.microsoft.com/azure/ai-services/openai/reference for the current list).

**Changing a deployment's capacity:** Azure CLI has no `deployment update` command — it fails with `'update' is misspelled or not recognized by the system`. To change capacity, either use Azure AI Foundry/Portal, or delete the deployment and recreate it with the `deployment create` command above (recreating fails if the old deployment still exists, or if you lack quota for the new capacity).

### 1. Clone and start Postgres

```bash
git clone <this-repo-url>
cd ai-pr-reviewer
docker-compose up -d
```

This starts a `postgres:16-alpine` container (`ai-pr-reviewer-db`) on port `5432` with database `ai_pr_reviewer`, user `postgres`, password `postgres`.

> **Note:** `spring-boot-api/src/main/resources/application.yml` defaults `DB_USERNAME`/`DB_PASSWORD` to `harshil`/`harshil@123`, which do **not** match the `docker-compose.yml` Postgres user (`postgres`/`postgres`). Override them explicitly (step 2) so the API can actually connect — otherwise it fails on startup with an authentication error.

### 2. Run the Spring Boot API

```bash
cd spring-boot-api
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export GITHUB_WEBHOOK_SECRET=<pick-any-secret-string>
export GITHUB_API_TOKEN=<your-github-personal-access-token>
export AZURE_SERVICEBUS_CONNECTION_STRING=<your-service-bus-connection-string>
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Confirm it's up:

```bash
curl http://localhost:8080/api/reviews
```

(should return `[]` on a fresh DB)

### 3. Expose the API and wire up the GitHub webhook

```bash
ngrok http 8080
```

Copy the `https://<random>.ngrok-free.app` URL ngrok prints, then in your GitHub repo:

**Settings → Webhooks → Add webhook**
- Payload URL: `https://<your-ngrok-url>/api/webhooks/github`
- Content type: `application/json`
- Secret: same value as `GITHUB_WEBHOOK_SECRET`
- Events: just the "Pull requests" event

Free ngrok URLs change every restart — re-update the webhook Payload URL if you restart ngrok. Use `http://127.0.0.1:4040` (ngrok's local web inspector) to debug raw request/response if deliveries fail.

### 4. Run the Azure Function

```bash
cd azure-function
cp local.settings.json.example local.settings.json
```

Edit `local.settings.json` and fill in:
- `AZURE_SERVICEBUS_CONNECTION_STRING` — same value used in step 2
- `GITHUB_API_TOKEN` — same token used in step 2
- `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`, `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_API_VERSION` — from your Azure OpenAI resource
- `SPRING_API_CALLBACK_URL` — leave as `http://localhost:8080/api/reviews/callback` for local runs

```bash
mvn clean package
mvn azure-functions:run
```

### 5. Try it end-to-end
Open a PR (or push a commit) in the GitHub repo you wired up in step 3. Within a minute you should see:
- A new row in `pull_request_events` (status `QUEUED` → `COMPLETED`)
- An AI-generated review comment thread on the PR itself
- `GET http://localhost:8080/api/reviews/{eventId}` returning the structured result

### Tearing down
```bash
docker-compose down       # stop Postgres (add -v to also wipe the volume/data)
```

## Deploying the Azure Function to Azure

Runs the same code as step 4, but on a real Function App in Azure instead of your machine — needed for a webhook flow that isn't tied to your laptop being on.

`azure-function/pom.xml` already defines the target app (`functionAppName`, `functionResourceGroup`, `functionAppRegion` properties, Linux runtime, Java 21) — the plugin creates the Function App automatically on first deploy if it doesn't exist yet.

```bash
az login

cd azure-function
mvn clean package
mvn azure-functions:deploy
```

The Function App only gets `FUNCTIONS_EXTENSION_VERSION` from `pom.xml` — every other setting from `local.settings.json` (step 4) must be pushed explicitly:

```bash
az functionapp config appsettings set \
  --name ai-pr-reviewer-func \
  --resource-group ai-pr-reviewer-rg \
  --settings \
    AZURE_SERVICEBUS_CONNECTION_STRING="<value>" \
    GITHUB_API_TOKEN="<value>" \
    AZURE_OPENAI_ENDPOINT="<value>" \
    AZURE_OPENAI_DEPLOYMENT="<value>" \
    AZURE_OPENAI_API_KEY="<value>" \
    AZURE_OPENAI_API_VERSION="<value>" \
    SPRING_API_CALLBACK_URL="<your-deployed-spring-boot-api-url>/api/reviews/callback"
```

`SPRING_API_CALLBACK_URL` must point at a publicly reachable Spring Boot API (not `localhost`) — either deploy the API too, or keep it reachable via ngrok for a hybrid local/cloud test.

Tail logs to confirm it's picking up queue messages:

```bash
func azure functionapp logstream ai-pr-reviewer-func
```

## API reference

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/webhooks/github` | POST | GitHub webhook receiver (HMAC-verified) |
| `/api/reviews/callback` | POST | Internal — Azure Function posts results here |
| `/api/reviews/{eventId}` | GET | Fetch a specific review result |
| `/api/reviews` | GET | List all reviewed pull requests |

## Design decisions worth calling out (interview talking points)

- **HMAC signature verification** on the webhook so only GitHub can trigger reviews, not an arbitrary POST to the endpoint.
- **JSON-mode / strict-schema prompting** for the LLM call — the model is instructed to return only a JSON object matching a fixed schema, which makes parsing deterministic instead of regex-scraping free text.
- **Fail-soft parsing** — if the LLM ever returns malformed JSON, the pipeline still records that a review ran instead of throwing away the whole result.
- **Diff truncation** — large diffs are truncated before hitting the LLM to control token cost and avoid context-window failures on huge PRs (e.g. vendored files, generated code, lockfiles).
- **Async hand-off via queue** — keeps the webhook responder fast and lets the expensive AI call scale independently and retry on failure without blocking GitHub's webhook delivery.

## Possible extensions
- Skip generated/vendored paths (`package-lock.json`, `*.min.js`) before sending to the LLM
- Auto-set PR review `event` to `REQUEST_CHANGES` when any comment has `severity: high`
- Add a lightweight dashboard (React) over `GET /api/reviews` to show review history and trends
- Cache repeated diffs/comments to cut duplicate LLM calls on force-pushes

## License
MIT
