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

* Azure OpenAI powered review generation
* Structured JSON output for deterministic processing
* Security, bug, and maintainability focused prompts

### 📊 Review Tracking

* Persist review history
* Query results via REST APIs
* Track review outcomes and status

### ☁️ Cloud-Native Design

* Spring Boot API
* Azure Service Bus
* Azure Functions
* Azure OpenAI
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
│ Azure Service Bus Queue     │
└─────────────────────────────┘
        │
        ▼
┌─────────────────────────────┐
│ Azure Function              │
│ Review Processor            │
└─────────────────────────────┘
        │
        ├─ Fetch PR Diff
        ├─ Call Azure OpenAI
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

### 3️⃣ Azure Function Processes Review

The function:

* Fetches the PR diff
* Sends the diff to Azure OpenAI
* Parses structured review output
* Creates GitHub review comments

### 4️⃣ Results Are Stored

Review findings are persisted and made available through APIs.

### 5️⃣ Developers Receive Feedback

Comments appear directly inside the GitHub Pull Request.

---

# 📦 Repository Structure

```text
ai-pr-reviewer/
├── spring-boot-api/
│   ├── Webhook Receiver
│   ├── Review Persistence
│   └── Query APIs
│
├── azure-function/
│   ├── Diff Fetching
│   ├── LLM Review Engine
│   └── Comment Publishing
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
| Messaging   | Azure Service Bus |
| Compute     | Azure Functions   |
| AI          | Azure OpenAI      |
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
