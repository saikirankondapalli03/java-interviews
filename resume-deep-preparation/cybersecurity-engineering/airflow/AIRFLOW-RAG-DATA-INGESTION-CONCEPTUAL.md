# Apache Airflow — Conceptual Guide for RAG Data Ingestion

*Use this to justify and speak to the resume bullet: "Built backend services for RAG system: **Airflow** data ingestion pipeline, AWS OpenSearch search backend (BM25 + vector embeddings), and FastAPI endpoints for LLM integration."*

---

## 1. What is Apache Airflow?

**Apache Airflow** is an open-source **workflow orchestration platform**. It lets you define, schedule, and monitor **pipelines** (sequences of tasks) as code.

- **Not a data processing engine**: It does not run Spark, Pandas, or heavy compute. It **orchestrates** when and in what order tasks run (e.g., “run this Python script,” “call this API,” “trigger this job on another system”).
- **DAG-based**: Pipelines are modeled as **Directed Acyclic Graphs (DAGs)**. Each node is a **task**; edges define dependencies (e.g., “task B runs only after task A succeeds”).
- **Python-native**: DAGs and tasks are defined in Python, which fits well with ML/RAG stacks (Python operators, hooks, custom code).

**One-line summary for interviews:** *"Airflow is a workflow orchestrator. We use it to schedule and coordinate the data ingestion pipeline that feeds our RAG system—crawling or pulling documents, chunking, embedding, and indexing into OpenSearch."*

---

## 2. Why Use Airflow for a RAG Data Ingestion Pipeline?

In a **RAG (Retrieval-Augmented Generation)** system you need:

1. **Ingest** documents (internal docs, APIs, files, web).
2. **Process** them (chunk, clean, normalize).
3. **Embed** chunks (e.g., via an embedding model or API).
4. **Index** into a search backend (e.g., AWS OpenSearch with vector + BM25).

Airflow fits because:

| Need | How Airflow helps |
|------|-------------------|
| **Scheduling** | Run ingestion on a schedule (e.g., daily, hourly) or on events. |
| **Dependencies** | Enforce order: “fetch → chunk → embed → index”; no indexing before embedding. |
| **Retries & failure handling** | Retry failed tasks, alert on failure, avoid partial broken state. |
| **Visibility** | UI to see run history, logs, and which step failed. |
| **Reusability** | Same DAG code for different sources (e.g., Confluence DAG, S3 DAG) with parameters. |

**Interview talking point:** *"We used Airflow to own the data side of the RAG pipeline—scheduling when ingestion runs, making sure chunking and embedding complete before we index into OpenSearch, and handling retries and monitoring so the search backend stays up to date."*

---

## 3. Core Concepts (Enough to Speak Confidently)

### 3.1 DAG (Directed Acyclic Graph)

- A **DAG** is the pipeline definition: a set of **tasks** and their **dependencies**.
- “Acyclic” means no loops (no task can depend on itself indirectly).
- Each DAG has an ID, optional schedule (cron or trigger-only), and default args (retries, timeouts, etc.).

### 3.2 Task

- A **task** is one unit of work in a DAG (e.g., “run this Python function,” “execute this Bash command,” “call this API”).
- Implemented via **Operators** (e.g., `PythonOperator`, `BashOperator`, `HttpOperator`) or **TaskFlow** (decorated Python functions).

### 3.3 Operator

- **Operators** define *how* a task runs (Python, Bash, HTTP, etc.).
- For RAG ingestion you’d typically use:
  - `PythonOperator` or `@task` (TaskFlow) for custom logic (chunking, calling embedding API, building index payloads).
  - Possibly `BashOperator` or external-system operators (e.g., S3, Lambda) for triggering or moving data.

### 3.4 Schedule

- **Cron** (e.g., `0 2 * * *` = 2 AM daily) or **timedelta** (e.g., every 6 hours).
- Or **trigger-only** (no schedule; run only when triggered manually or by an API/event).

### 3.5 Execution and Run

- A **DAG run** is one execution of the DAG (one “instance” of the pipeline).
- **Task instance** = one run of a single task within that DAG run.
- Airflow tracks state: queued, running, success, failed, skipped.

**Interview recap:** *"Our RAG ingestion is one DAG. It has tasks like fetch documents, chunk text, call the embedding service, and index into OpenSearch. We set a schedule—e.g., nightly—and Airflow makes sure each step runs in order and we can retry or debug from the UI."*

---

## 4. Conceptual Shape of a RAG Ingestion DAG

A minimal mental model:

```
[Fetch / Crawl]  →  [Chunk & Normalize]  →  [Generate Embeddings]  →  [Index to OpenSearch]
       (task 1)            (task 2)                  (task 3)                 (task 4)
```

- **Task 1 – Fetch**: Pull documents from source(s)—e.g., S3, Confluence, internal API—into a staging area or in-memory for the pipeline.
- **Task 2 – Chunk & normalize**: Split documents into chunks (e.g., by size or semantics), clean text, attach metadata (source, doc ID).
- **Task 3 – Embed**: For each chunk, call an embedding model/API to get vector embeddings. May use batching and retries.
- **Task 4 – Index**: Write chunks + embeddings (and optionally raw text for BM25) to AWS OpenSearch (vector index + keyword index for hybrid search).

You can extend this with:

- **Multiple source DAGs** (one DAG per source, or one DAG with branch tasks).
- **Idempotency** (e.g., upsert by doc/chunk ID so re-runs don’t duplicate).
- **Backfill** (Airflow’s backfill feature to reprocess historical data).

**Interview line:** *"The Airflow pipeline had clear stages: fetch from our sources, chunk and normalize, run embeddings, then index into OpenSearch so the RAG search backend—BM25 plus vector—always had fresh data."*

---

## 5. How This Fits Your Resume Bullet

Your bullet lists three pieces:

1. **Airflow data ingestion pipeline** — Orchestrates *when* and *in what order* ingestion runs; feeds the RAG knowledge base.
2. **AWS OpenSearch search backend (BM25 + vector embeddings)** — Where the ingested chunks and embeddings live; used at query time for retrieval.
3. **FastAPI endpoints for LLM integration** — Application layer that uses OpenSearch for retrieval and an LLM for generation.

Airflow is the **upstream data pipeline**; OpenSearch is the **store**; FastAPI is the **query-time API**. Saying *"Airflow handles the data ingestion that keeps OpenSearch populated for the RAG system"* ties the three together.

---

## 6. Quick Interview Q&A

**Q: What is Airflow?**  
A: An open-source workflow orchestration platform. We use it to define, schedule, and monitor our RAG data ingestion pipeline—fetching, chunking, embedding, and indexing into OpenSearch.

**Q: Why Airflow and not cron + scripts?**  
A: We needed dependencies between steps (e.g., don’t index until embeddings are done), retries, visibility into failures, and a single place to manage multiple ingestion flows. Airflow gives us that as code with a UI.

**Q: What did your Airflow pipeline do?**  
A: It was the ingestion DAG for the RAG system: fetch documents from our sources, chunk and normalize text, generate embeddings, and index into AWS OpenSearch so the search backend (BM25 + vectors) stayed up to date for the FastAPI/LLM layer.

**Q: Have you written DAGs?**  
A: I’m relatively new to Airflow; my involvement was [adjust to your reality: e.g., “designing the pipeline and task boundaries” / “integrating with our embedding service and OpenSearch” / “defining the schedule and failure handling”]. I understand the concepts—DAGs, tasks, operators, scheduling—and how they fit into the RAG data flow.

---

## 7. One-Page Cheat Sheet

| Term | Meaning |
|------|--------|
| **DAG** | Pipeline as a directed acyclic graph of tasks. |
| **Task** | One unit of work; implemented by an operator or TaskFlow function. |
| **Operator** | Recipe for a task (Python, Bash, HTTP, etc.). |
| **DAG run** | One execution of the entire DAG. |
| **Task instance** | One execution of a single task. |
| **Schedule** | When the DAG runs (cron / timedelta / trigger-only). |

**RAG + Airflow in one sentence:**  
*Airflow runs the data ingestion pipeline that keeps our RAG search backend (OpenSearch with BM25 and vector embeddings) populated, so the FastAPI/LLM layer can retrieve relevant context at query time.*

---

*File purpose: Conceptual backup for the TIAA RAG bullet. Use the “Quick Interview Q&A” and “One-Page Cheat Sheet” for recall before interviews.*
