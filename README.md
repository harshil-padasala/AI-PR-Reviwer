# 🤖 AI-Powered PR Reviewer

An event-driven code review platform that automatically analyzes GitHub Pull Requests using an LLM and posts actionable review comments directly back to the PR.

Instead of waiting for a human reviewer to spot common issues, the system performs an automated first-pass review immediately after every push, identifying:

* 🔒 Security vulnerabilities
* 🐛 Potential bugs
* ⚠️ Null-safety issues
* 📈 Performance concerns
* 🧹 Maintainability problems
* 💡 Code quality improvements

This reduces review latency, improves consistency, and allows engineers to focus on architecture and business logic rather than repetitive code checks.

---

## ✨ Key Features

### 🚀 Automated Pull Request Reviews

* Triggered automatically on PR open/update
* Reviews code before a human reviewer even opens the diff
* Posts inline comments directly on GitHub

### ⚡ Event-Driven Architecture

* Fast webhook response (<10 seconds)
* Asynchronous review processing
* Independent scaling of API and AI workloads

### 🧠 AI-Powered Analysis

* OpenAI API powered review generation
* Structured JSON output for deterministic processing
* Security, bug, and maintainability focused prompts

### 📊 Review Tracking

* Persist review history
* Query results via REST APIs
* Track review outcomes and status

### ☁️ Cloud-Native Design

* Spring Boot API
* Amazon SQS
* AWS Lambda
* OpenAI API
* PostgreSQL

---

# 🏗 Architecture

```text
GitHub Pull Request
        │
        │ Webhook (HMAC Signed)
        ▼
┌─────────────────────────────┐
│ Spring Boot API             │
│ Webhook Receiver            │
└─────────────────────────────┘
        │
        │ Publish Event
        ▼
┌─────────────────────────────┐
│ Amazon SQS Queue            │
└─────────────────────────────┘
        │
        ▼
┌─────────────────────────────┐
│ AWS Lambda Worker           │
│ Review Processor            │
└─────────────────────────────┘
        │
        ├─ Fetch PR Diff
        ├─ Call OpenAI API
        ├─ Generate Findings
        ├─ Post GitHub Comments
        │
        ▼
┌─────────────────────────────┐
│ Spring Boot API             │
│ Persist Review Results      │
└─────────────────────────────┘
        │
        ▼
Review Dashboard / APIs
```

Architecture source:

---

# 🎯 Why This Project Exists

Every engineering team struggles with:

* Slow review turnaround
* Reviewer overload
* Inconsistent code quality checks
* Missed security and reliability issues

This project automates the repetitive first-pass review so engineers can spend their time on:

* Architecture decisions
* System design
* Business requirements
* Product quality

instead of repeatedly flagging the same common issues.

---

# 🔄 End-to-End Flow

### 1️⃣ Developer Opens or Updates a PR

GitHub sends a signed webhook event.

### 2️⃣ Spring Boot API Receives Event

The API:

* Verifies the HMAC signature
* Persists the event
* Publishes a queue message

### 3️⃣ AWS Lambda Worker Processes Review

The worker:

* Fetches the PR diff
* Sends the diff to OpenAI API
* Parses structured review output
* Creates GitHub review comments

### 4️⃣ Results Are Stored

Review findings are persisted and made available through APIs.

### 5️⃣ Developers Receive Feedback

Comments appear directly inside the GitHub Pull Request.

---

# 📦 Repository Structure
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
        │                     Amazon SQS Queue
        │                              │
        │                              ▼
        │                     AWS Lambda Worker (SQS Event Source)
        │                        1. fetch PR diff (GitHub API)
        │                        2. send diff to OpenAI API
        │                        3. parse structured JSON response
        │                        4. post review comments (GitHub API)
        ◄──────────────────────  5. POST result back to Spring Boot API
        ▼
   review persisted, viewable via GET /api/reviews
```

**Why split the work this way:** the webhook handler needs to respond to GitHub in under 10 seconds or the delivery is marked failed. The actual review (diff fetch + LLM call) can take much longer and shouldn't block that response — so it's handed off to an async, independently-scalable worker via a queue. This is a standard "sync API + async event-driven compute" pattern used for any slow, bursty workload.

## Tech stack

| Layer | Tech |
|---|---|
| API | Spring Boot 3, Spring Data JPA, PostgreSQL, Actuator |
| Messaging | Amazon SQS |
| Compute | AWS Lambda (Java 21 RequestHandler event-driven worker) |
| Orchestration | AWS SAM (`aws-lambda/template.yaml`) / Kubernetes Helm chart (`helm/`) |
| AI | OpenAI API (chat completions, JSON-mode) |
| External API | GitHub REST API (diff fetch, review/comment posting) |

## Project structure

```text
ai-pr-reviewer/
├── spring-boot-api/
│   ├── Webhook Receiver
│   ├── Review Persistence
│   └── Query APIs
│
├── aws-lambda/
│   ├── Diff Fetching
│   ├── LLM Review Engine
│   ├── Comment Publishing
│   └── AWS SAM Template & SQS Event Payload
│
├── docker-compose.yml
└── docs/
```

Based on project structure defined in the repository.

---

# ⚙️ Technology Stack

| Layer       | Technology        |
| ----------- | ----------------- |
| Backend API | Spring Boot 3     |
| Persistence | PostgreSQL        |
| Messaging   | Amazon SQS        |
| Compute     | AWS Lambda        |
| AI          | OpenAI API        |
| Integration | GitHub REST API   |

Source:

---

# 🔥 Engineering Highlights

### Secure Webhook Processing

* HMAC signature validation
* Prevents unauthorized event injection

### Structured AI Output

* JSON-schema driven prompting
* Predictable downstream processing

### Fail-Soft Processing

* Invalid AI responses don't break the workflow

### Cost-Aware Reviewing

* Diff truncation prevents excessive token usage

### Async Event Architecture

* Webhook stays fast
* AI processing scales independently

These are strong interview discussion points and demonstrate production-oriented system design.

---
* Automated pull request analysis and inline review generation through LLM-powered code inspection.
* Built secure webhook verification, structured AI response handling, and scalable queue-based processing.
├── spring-boot-api/     # Webhook receiver, persistence, review query API, SQS publisher
├── aws-lambda/          # SQS-triggered Lambda worker (diff fetch + LLM review + comment posting)
├── helm/ai-pr-reviewer/ # Helm chart (api + worker Deployments, optional Postgres subchart)
├── k8s/                 # Plain k8s manifests (non-Helm alternative)
├── docker-compose.yml   # Local Postgres for the API
└── docs/                # Architecture diagram, demo


## Running locally — step by step

### Prerequisites
- Java 21+, Maven
- Docker (for local Postgres)
- [ngrok](https://ngrok.com) (or similar) to expose your local API to GitHub's webhook delivery
- An AWS Account (Amazon SQS queue + AWS Lambda) & [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
- A GitHub repo you can add a webhook to, and a personal access token with `repo` scope

### Getting your credentials

Every value below is required somewhere in steps 1-4. What it is, why the project needs it, and where to get it:

| Credential | What it is | Why needed | Where to get it |
|---|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | A shared secret string you invent yourself — not issued by GitHub | Spring Boot API uses it to verify the HMAC signature on incoming webhook payloads, so only real GitHub deliveries (not a random POST from anyone) trigger a review | Make one up yourself, e.g. `openssl rand -hex 20`. Same string goes into the API env var **and** the GitHub webhook's "Secret" field |
| `GITHUB_API_TOKEN` | GitHub personal access token | Both the API and the AWS Lambda worker call GitHub's REST API (fetch PR diff, post review comments) — that needs authentication | GitHub → click your profile picture → **Settings** → **Developer settings** (bottom of left sidebar) → **Personal access tokens** → **Tokens (classic)** → **Generate new token (classic)** → check the `repo` scope → **Generate token** → copy it immediately, it's shown only once. Direct link: https://github.com/settings/tokens |
| ngrok URL | A public HTTPS URL that tunnels to your local `localhost:8080` | GitHub's webhook delivery needs a public internet address to POST to — it can't reach your laptop's `localhost` directly | Install ngrok, run `ngrok config add-authtoken <token>` once (token from https://dashboard.ngrok.com/get-started/your-authtoken after free signup at https://dashboard.ngrok.com/signup), then run `ngrok http 8080` — the `https://<random>.ngrok-free.app` line it prints (also visible at `http://127.0.0.1:4040`) is your URL. It changes every time you restart ngrok unless you're on a paid plan with a reserved domain |
| `AWS_SQS_QUEUE_URL` | Amazon SQS Queue URL (e.g. `https://sqs.us-east-1.amazonaws.com/123456789012/pr-review-requests`) | The API publishes each webhook event onto this queue; AWS Lambda consumes from it — this is the async hand-off described in [Architecture](#architecture) | AWS CLI or AWS SAM output — see [Create SQS Queue via AWS CLI](#create-sqs-queue-via-aws-cli) below |
| `AWS_REGION` | AWS Region (e.g. `us-east-1`) | Specifies the region for the AWS SQS SDK client | Your target AWS region (defaults to `us-east-1`) |
| `OPENAI_API_KEY` | OpenAI API Key | The AWS Lambda worker sends the PR diff to OpenAI API to generate structured JSON reviews | OpenAI Platform Settings → [API Keys](https://platform.openai.com/api-keys) |
| `OPENAI_MODEL` | OpenAI Model Name | Target LLM model for PR code review | Default is `gpt-4o-mini` (or `gpt-4o`) |
| `SPRING_API_CALLBACK_URL` | URL the AWS Lambda worker POSTs its finished review back to | Closes the loop — persists the review result and makes it queryable via `GET /api/reviews/{eventId}` | For local runs it's `http://localhost:8080/api/reviews/callback` (or your deployed API public URL) |

#### Create SQS Queue via AWS CLI

```bash
aws configure   # ensure your AWS CLI is authenticated

aws sqs create-queue \
  --queue-name pr-review-requests \
  --region us-east-1

# get the QueueUrl value for AWS_SQS_QUEUE_URL
aws sqs --profile myprofile get-queue-url \
  --queue-name pr-review-requests \
  --region us-east-1 \
  --query QueueUrl \
  --output text
```

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
export AWS_SQS_QUEUE_URL=<your-aws-sqs-queue-url>
export AWS_REGION=us-east-1
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

### 4. Run and test the AWS Lambda worker locally

You can test the Lambda handler locally using the **AWS SAM CLI** before deploying to cloud infrastructure:

```bash
cd aws-lambda

# 1. Package the Lambda Jar
mvn clean package

# 2. Invoke the Lambda locally with a sample SQS event
sam local invoke PRReviewerLambdaFunction -e events/sqs_event.json
```

This runs the Java 21 Lambda worker inside a containerized local runtime environment, simulates an SQS event trigger, fetches the PR diff, calls the LLM, and posts review comments back to GitHub.

---

## 🚀 Deploying AWS Lambda & SQS to AWS

You have two simple options to deploy the worker and queue to production on AWS:

### Option A: Deploy using AWS SAM CLI (Recommended)

The `aws-lambda/template.yaml` defines the serverless architecture (SQS queue, DLQ, IAM permissions, Lambda function, and event source mapping):

```bash
cd aws-lambda

# Build the SAM application
sam build

# Deploy interactively to AWS
sam deploy --guided \
  --stack-name ai-pr-reviewer \
  --region us-east-1 \
  --parameter-overrides \
    GitHubApiToken="<your-github-token>" \
    SpringApiCallbackUrl="https://<your-api-domain>/api/reviews/callback" \
    OpenAiApiKey="<your-openai-api-key>" \
    OpenAiModel="gpt-4o-mini"
```

SAM will automatically provision:
- SQS Standard Queue (`pr-review-requests`) & Dead Letter Queue (`pr-review-requests-dlq`).
- AWS Lambda Function (`ai-pr-reviewer-worker`) configured with Java 21 runtime.
- SQS Event Source Mapping (triggers Lambda whenever a message arrives).

### Option B: Manual AWS CLI Deployment

If you prefer using the AWS CLI directly:

```bash
# 1. Create IAM execution role for Lambda
aws iam create-role \
  --role-name AIPrReviewerLambdaRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }]
  }'

aws iam attach-role-policy \
  --role-name AIPrReviewerLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole

# 2. Package the Lambda JAR
cd aws-lambda && mvn clean package

# 3. Create the Lambda function
aws lambda create-function \
  --function-name ai-pr-reviewer-worker \
  --runtime java21 \
  --role arn:aws:iam::<YOUR_ACCOUNT_ID>:role/AIPrReviewerLambdaRole \
  --handler com.aipr.lambda.LambdaHandler::handleRequest \
  --zip-file fileb://target/aws-lambda.jar \
  --timeout 60 \
  --memory-size 512 \
  --environment "Variables={GITHUB_API_TOKEN=<token>,SPRING_API_CALLBACK_URL=<url>,OPENAI_API_KEY=<openai_key>,OPENAI_MODEL=gpt-4o-mini}"

# 4. Map the SQS queue trigger to Lambda
aws lambda create-event-source-mapping \
  --function-name ai-pr-reviewer-worker \
  --batch-size 1 \
  --event-source-arn arn:aws:sqs:us-east-1:<YOUR_ACCOUNT_ID>:pr-review-requests
```

---

## Deploying Spring Boot API with Docker / Kubernetes

Both services are plain containers — build once, run anywhere:

```bash
docker build -t ai-pr-reviewer-api:1.0.0 ./spring-boot-api
docker build -t ai-pr-reviewer-lambda:1.0.0 ./aws-lambda
```

### Helm (recommended for Spring Boot API)

```bash
cp helm/ai-pr-reviewer/values-secret.yaml.example helm/ai-pr-reviewer/values-secret.yaml
# fill in values-secret.yaml with real credentials — it's gitignored

helm dependency build helm/ai-pr-reviewer
helm install ai-pr-reviewer helm/ai-pr-reviewer -f helm/ai-pr-reviewer/values-secret.yaml
```

`postgresql.enabled` defaults to `false` — supply `database.external.{host,port,name,username}`
for an external/managed Postgres, or set `--set postgresql.enabled=true` (and
`postgresql.auth.password`) for a dev-only in-cluster instance via the bundled
Bitnami subchart. See `helm/ai-pr-reviewer/values.yaml` for the full set of
knobs (replica counts, resources, ingress host, autoscaling).

### Plain manifests (no Helm)

```bash
cp k8s/secret.yaml.example k8s/secret.yaml
# fill in k8s/secret.yaml, edit k8s/configmap.yaml's DB_URL/DB_USERNAME

kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/api-deployment.yaml -f k8s/api-service.yaml -f k8s/api-ingress.yaml
```

The API's Ingress is what GitHub's webhook must reach (`/api/webhooks/github`);
the worker calls back into the API over its in-cluster Service, not the Ingress.

## API reference

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/webhooks/github` | POST | GitHub webhook receiver (HMAC-verified) |
| `/api/reviews/callback` | POST | Internal — AWS Lambda worker posts results here |
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
