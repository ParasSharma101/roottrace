# RootTrace — AI Production Incident Investigator

> **Event-driven observability system correlating alerts, distributed logs, deployments, and GitHub PR diffs into grounded root-cause hypotheses with Gemini AI, human-in-the-loop verification, and automated postmortem export.**

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js 14](https://img.shields.io/badge/Next.js-14.2-black.svg)](https://nextjs.org/)
[![Kafka 3.7](https://img.shields.io/badge/Apache%20Kafka-3.7%20KRaft-red.svg)](https://kafka.apache.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Gemini AI](https://img.shields.io/badge/Gemini%20API-1.5%20Pro-purple.svg)](https://ai.google.dev/)

---

## Architecture Overview

```
                      +---------------------------------------+
                      |       Alert Sources & Telemetry       |
                      |  (Datadog, PagerDuty, NewRelic, Apps) |
                      +-------------------+-------------------+
                                          |
                        HTTP POST Webhooks (/api/v1/webhooks)
                                          v
                      +---------------------------------------+
                      |         Kafka Ingestion Layer         |
                      |   [raw-logs]      [incoming-alerts]   |
                      |   (3 Partitions)   (3 Partitions)     |
                      |          |                |           |
                      |          v                v           |
                      |     [raw-logs-dlq]  [alerts-dlq]      |
                      +-------------------+-------------------+
                                          |
                      +-------------------+-------------------+
                      |       Event Consumers (Java 21)       |
                      |  - Virtual Thread Concurrency         |
                      |  - Alert Storm Deduplication (15m)    |
                      |  - High-Throughput Log Micro-Batching |
                      +-------------------+-------------------+
                                          |
                 +------------------------+------------------------+
                 |                                                 |
                 v                                                 v
+---------------------------------+               +---------------------------------+
|       PostgreSQL 16 Store       |               |       Redis 7 Cache Store       |
| - Incidents & Alerts            |               | - Time-Window Context Bundles   |
| - Deployments & GitHub PR Diffs |               | - GitHub API Commit Cache       |
| - Log Events (Decoupled)        |               |   (TTL: 10m - 2h)               |
| - Grounded AI Hypotheses        |               +---------------------------------+
| - SRE Collaboration Notes       |
+----------------+----------------+
                 |
                 v
+-------------------------------------------------------------------+
|               RootTrace AI Correlation & Reasoning Engine         |
|  1. Time-Window Slicing [-30m, +5m relative to alert trigger]     |
|  2. Stack Trace Compression (30 raw logs -> 1 signature)          |
|  3. Gemini AI / SRE Heuristic Engine Root Cause Synthesis         |
|  4. Strict Grounding Validation (Hallucination Stripping)         |
+--------------------------------+----------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|                  Next.js 14 SRE Command Center                    |
|  - Real-time Incident Dashboard with SEV1/2/3 Filtering           |
|  - Interactive Chronological Timeline Visualizer                  |
|  - Grounded AI Hypotheses with One-Click Verify / Reject          |
|  - Audit Collaboration Log & Instant Markdown Postmortem Export   |
+-------------------------------------------------------------------+
```

---

## Defensive Engineering & Failure Prevention Log

RootTrace was architected specifically to eliminate the critical failure modes that break automated incident response in production:

| Potential Failure Mode | Root Cause in Naive Systems | RootTrace Architectural Defense |
| :--- | :--- | :--- |
| **Timeline Paradox** | Coupling logs directly to incident IDs at write time fails because logs are emitted *before* an incident is declared. | Decoupled write path: logs stream to `raw-logs` topic and persist independently. Dynamic correlation slices `[-30m, +5m]` around alert trigger timestamp. |
| **Alert Storm Flooding** | Cascading failures trigger hundreds of identical alerts within seconds, spawning hundreds of duplicate incidents. | Alert deduplication window: alerts on the same service within 15 minutes automatically link to the existing active `Incident` record instead of spawning duplicates. |
| **Database Connection Exhaustion** | Heavy parallel write operations from unthrottled ingestion threads deplete DB connection pools. | Virtual Threads with bounded `saveAll()` micro-batching inside dedicated write transactions. |
| **LLM Hallucinations** | Generative AI models invent non-existent log IDs, file paths, or PR numbers. | Programmatic `GroundingValidator` verifies every cited `referenceId` against actual PostgreSQL records; hallucinated citations are stripped and confidence penalized. |
| **Race Conditions in State Transitions** | Asynchronous AI investigation transitions incidents from `OPEN` to `INVESTIGATING` while consumers or queries are running. | State queries check both `OPEN` and `INVESTIGATING` states; cache layers use Redis atomic keys with TTLs. |
| **Timeline UI Performance Degeneration** | Thousands of error logs during a severe outage inflate timeline payloads into multi-megabyte payloads. | `LogFingerprinter` groups identical stack traces into canonical signatures, capping timeline log events to 15 representative clusters. |

---

## Tech Stack & Architecture Decisions

- **Backend:** Java 21 LTS, Spring Boot 3.3.5, Spring Data JPA, Hibernate 6, Virtual Threads (`spring.threads.virtual.enabled: true`).
- **Database & Storage:** PostgreSQL 16 (relational integrity + JSONB for telemetry) with Flyway migrations.
- **Event Bus:** Apache Kafka 3.7 (native KRaft mode, no ZooKeeper) with 3 partitions and dedicated Dead Letter Queues (DLQ).
- **Caching & Ephemeral State:** Redis 7 (incident context bundles + GitHub PR metadata cache).
- **AI Orchestration:** Google Gemini API (`gemini-1.5-pro` / `gemini-1.5-flash`) with fallback deterministic SRE heuristic engine.
- **Frontend:** Next.js 14 (App Router), React 18, TypeScript, Tailwind CSS, Lucide React.
- **Packaging:** Multi-stage Dockerfiles (`eclipse-temurin:21-jre-alpine` + `node:20-alpine` standalone).

---

## Quickstart Guide

### Prerequisites
- Docker & Docker Compose
- Java 21 JDK (`java -version`)
- Node.js 20+ & npm (`node -v`)

### 1. Start Local Infrastructure (Postgres, Redis, Kafka)
```bash
docker compose up -d
```
Verify all 3 containers are healthy:
```bash
docker compose ps
```
- PostgreSQL: `localhost:5433` (mapped from 5432)
- Redis: `localhost:6379`
- Kafka (KRaft): `localhost:9092`

### 2. Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Add your Gemini API key (optional for local heuristics mode, required for live LLM):
```properties
GEMINI_API_KEY=your_gemini_api_key_here
GITHUB_TOKEN=your_optional_github_token
```

### 3. Run Backend Application
```bash
cd backend
./mvnw clean spring-boot:run
```
Backend will start on `http://localhost:8080`.

### 4. Run Frontend Dashboard
```bash
cd frontend
npm install
npm run dev
```
Frontend will be available at `http://localhost:3000`.

---

## Production Docker Deployment

To launch the full 5-tier production environment (Postgres + Redis + Kafka + Spring Boot + Next.js):
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
Access the application:
- Next.js SRE Console: `http://localhost:3000`
- Spring Boot REST API: `http://localhost:8080`
- Actuator Health Check: `http://localhost:8080/actuator/health`

---

## Running the Automated Test Suite

Backend integration tests use disposable PostgreSQL, Redis, and Kafka containers. Install JDK 21, set `JAVA_HOME`, and start Docker before running them. The development Compose stack is not required, and test provider credentials are overridden to avoid live Gemini/GitHub requests.

```bash
cd backend
./mvnw -B test
```

On Windows PowerShell, use `.\mvnw.cmd -B test` from `backend`.

The suite covers repository persistence, webhook validation and authorization, Kafka publishing/consumption, alert deduplication, correlation and log compression, Redis caching, heuristic AI investigation, citation grounding, account/workspace APIs, and incident review/postmortem workflows.

See [backend/TESTING.md](backend/TESTING.md) for lifecycle, isolation boundaries, and focused test commands.

---

## API Reference

### 1. Ingestion Webhooks
- `POST /api/v1/webhooks/logs`: Ingest single or batch log records.
- `POST /api/v1/webhooks/alerts/{source}`: Ingest alerts from Datadog, PagerDuty, Prometheus, etc.

### 2. Incident Workspace
- `GET /api/v1/incidents`: List incidents with `status`, `severity`, and `serviceName` filters.
- `GET /api/v1/incidents/{id}`: Detailed incident view with alerts, hypotheses, and notes.
- `GET /api/v1/incidents/{id}/timeline`: Chronologically aggregated timeline (Deployments, PRs, Alerts, Error Logs, Hypotheses, Notes).
- `PATCH /api/v1/incidents/{id}/status`: Transition status (`INVESTIGATING`, `OPEN`, `RESOLVED`).

### 3. Human-in-the-Loop Verification
- `POST /api/v1/incidents/{id}/hypotheses/{hypothesisId}/verify`: Mark hypothesis as `VERIFIED` and log engineer audit note.
- `POST /api/v1/incidents/{id}/hypotheses/{hypothesisId}/reject`: Mark hypothesis as `REJECTED` with reason.
- `POST /api/v1/incidents/{id}/notes`: Add engineer notes and mitigation logs.

### 4. Automated Postmortem Export
- `GET /api/v1/incidents/{id}/postmortem`: JSON postmortem object with structured Markdown content.
- `GET /api/v1/incidents/{id}/postmortem/export`: Direct file download (`postmortem-{id}.md`) with `Content-Disposition`.

---

## Incident Demo Walkthrough

1. Open `http://localhost:3000` in your browser.
2. Click **"Simulate Scenario A"** in the top navigation bar:
   - Injects a `ConnectionPoolExhausted` alert on `payment-service`.
   - Links to correlated `v2.4.1` database migration deployment and PR `#42`.
   - AI correlates logs, PR diff, and generates a grounded hypothesis: *Database lock contention from migration script*.
3. Click the incident card to enter the **Investigation Room**:
   - Inspect the **Correlated Timeline** showing the exact chronology from deployment to failure.
   - Review the **AI Root Causes** tab and verify the evidence citations.
   - Click **"Verify Root Cause"** to confirm the diagnosis as an SRE.
   - Transition status to **RESOLVED**.
   - Navigate to the **Automated Postmortem** tab and click **"Download .md"** to export the ready-to-share postmortem.
