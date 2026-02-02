# arXiv Paper Curator – Airflow Pipeline Build From Bottom Up

Production-standard design for the Apache Airflow pipeline. Describes how the pipeline is built for production (Celery/K8s, secrets, PostgreSQL task handoff, observability). The `arxiv-paper-curator` repo uses Docker Compose for local dev; this doc reflects the production target.

---

## 1. Project Context

The **arxiv-paper-curator** project is a RAG (Retrieval-Augmented Generation) system that:

- Ingests arXiv papers (CS.AI category)
- Parses PDFs and extracts text
- Stores metadata + content in PostgreSQL
- Chunks text, generates embeddings, indexes in OpenSearch for hybrid search
- Exposes an API for search and Q&A

**Airflow** runs the scheduled ingestion pipeline: fetch → store → chunk/embed → index → report.

---

## 2. Infrastructure Layer (Bottom)

### 2.1 Services Architecture

| Service        | Role                                      | Production note                                 |
|----------------|-------------------------------------------|-------------------------------------------------|
| **PostgreSQL** | App DB: papers, pipeline_runs; Airflow metadata DB (separate) | Managed DB (RDS, Cloud SQL); HA, backups        |
| **OpenSearch** | Hybrid search index (vector + keyword)    | Managed (OpenSearch Service, Elastic Cloud)     |
| **Airflow**    | Scheduler + workers orchestrate DAGs      | CeleryExecutor or K8sExecutor; workers scale    |
| **API**        | FastAPI app (search, RAG)                 | Behind LB, horizontal scaling                   |
| **LLM**        | Q&A generation                            | Managed (OpenAI, Anthropic) or self-hosted      |

**Deployment**: Docker Compose for dev; production uses Kubernetes (Astronomer, MWAA, GCP Composer) or similar. DAGs deployed via Git-sync or baked into worker image.

### 2.2 Airflow Container (`airflow/Dockerfile`)

1. **Base**: `python:3.12-slim`
2. **System deps**: `libpq-dev`, `poppler-utils`, `tesseract-ocr`, `build-essential`
3. **User**: non-root (`airflow` UID 50000)
4. **Airflow**: `apache-airflow[postgres,celery]==2.10.3` + `psycopg2-binary` (CeleryExecutor for production)
5. **Project deps**: `requirements-airflow.txt` (httpx, sqlalchemy, pydantic, docling, opensearch-py)
6. **Entrypoint**: init, webserver, scheduler; workers run separately (Celery)

### 2.3 Production Entrypoint / Startup

- `airflow db init` (managed migrations in prod)
- Admin user from secrets (Vault, K8s Secrets, env vars) — never hardcoded
- Webserver + Scheduler in main pod; Celery workers in separate pods
- Health checks: `/health` on webserver; scheduler liveness

### 2.4 DAG and Code Deployment

- **Production**: DAGs in Git repo; Git-sync sidecar or CI/CD builds worker image with DAGs + `src/`
- **No bind mounts** of host paths in production
- `PYTHONPATH` includes app source so tasks can import from `src`

---

## 3. Shared Application Layer

Airflow DAGs **reuse** the main application’s services instead of duplicating logic.

### 3.1 Configuration (`src/config.py`)

- Pydantic `Settings` with env var support
- Nested config: `ArxivSettings`, `PDFParserSettings`, `ChunkingSettings`, `OpenSearchSettings`
- **Production**: Config from environment; secrets (DB URL, API keys) from secrets manager or K8s Secrets, never committed

### 3.2 Database (`src/db/factory.py`, `src/db/interfaces/postgresql.py`)

- `make_database()` → `PostgreSQLDatabase` (SQLAlchemy) — **app DB** (papers, pipeline_runs)
- **Production**: App DB and Airflow metadata DB are separate (different connections). Airflow metadata for DAG state, task runs; app DB for domain data.

### 3.3 Data Model

**Paper** (`src/models/paper.py`):

- arxiv_id, title, authors, abstract, categories, published_date, pdf_url
- Parsed content: raw_text, sections, references, parser_metadata
- created_at, updated_at

**PipelineRun** (production standard for task handoff):

- `id`, `execution_date`, `target_date`
- `papers_fetched`, `papers_stored`, `papers_indexed`, `chunks_created`, `chunks_indexed`
- `status`, `created_at`

Tasks pass data via PostgreSQL. No XCom.

### 3.3.1 Database Schema (SQL)

Document lo reference chesina tables — `papers` and `pipeline_runs`. SQLAlchemy/Alembic tho create cheyachu, or direct SQL:

```sql
-- Pipeline runs: each DAG run creates one row; tasks update it
CREATE TABLE pipeline_runs (
    id              SERIAL PRIMARY KEY,
    execution_date  TIMESTAMPTZ NOT NULL,
    target_date     DATE NOT NULL,
    papers_fetched  INTEGER DEFAULT 0,
    papers_stored   INTEGER DEFAULT 0,
    papers_indexed  INTEGER DEFAULT 0,
    chunks_created  INTEGER DEFAULT 0,
    chunks_indexed  INTEGER DEFAULT 0,
    status          VARCHAR(50) NOT NULL,  -- 'running', 'fetch_complete', 'index_complete', 'failed'
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Papers: fetched from arXiv, linked to pipeline run
CREATE TABLE papers (
    id                SERIAL PRIMARY KEY,
    pipeline_run_id   INTEGER NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    arxiv_id          VARCHAR(50) NOT NULL UNIQUE,
    title             TEXT NOT NULL,
    authors           TEXT,                -- JSON array or comma-separated
    abstract          TEXT,
    categories        TEXT,                -- e.g. 'cs.AI'
    published_date    DATE,
    pdf_url           TEXT,
    raw_text          TEXT,                -- parsed content
    sections          JSONB,               -- structured sections
    references        JSONB,               -- parsed references
    parser_metadata   JSONB,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_papers_pipeline_run_id ON papers(pipeline_run_id);
CREATE INDEX idx_papers_arxiv_id ON papers(arxiv_id);
CREATE INDEX idx_pipeline_runs_execution_date ON pipeline_runs(execution_date);
```

### 3.4 Core Services (factories + clients)

| Service            | Location                  | Responsibility                               |
|--------------------|---------------------------|----------------------------------------------|
| arXiv client       | `src/services/arxiv/`     | Fetch metadata, download PDFs from arXiv API |
| PDF parser         | `src/services/pdf_parser/`| Parse PDFs with Docling                      |
| Metadata fetcher   | `src/services/metadata_fetcher.py` | Orchestrate fetch + parse + store   |
| OpenSearch client  | `src/services/opensearch/`| Create indices, bulk index, search           |
| Hybrid indexer     | `src/services/indexing/`  | Chunk text, generate embeddings, index       |
| Embeddings         | `src/services/embeddings/`| Jina AI embeddings (1024-d vectors)          |

---

## 4. Airflow DAG Layer

### 4.1 Directory Layout

```
airflow/
├── Dockerfile
├── entrypoint.sh
├── requirements-airflow.txt
└── dags/
    ├── arxiv_paper_ingestion.py    # Production ingestion DAG
    └── arxiv_ingestion/
        ├── __init__.py
        ├── common.py               # Cached services
        ├── setup.py                # Environment setup
        ├── fetching.py             # Fetch & store papers
        ├── indexing.py             # Chunk, embed, index
        └── reporting.py            # Generate report
```

### 4.2 Service Wiring (`arxiv_ingestion/common.py`)

`get_cached_services()` uses `@lru_cache(maxsize=1)` per worker process to return:

- arxiv_client, pdf_parser, database, metadata_fetcher, opensearch_client

Imports from `src.db.factory`, `src.services.arxiv.factory`, etc. Connection pooling handled by SQLAlchemy and OpenSearch clients.

### 4.3 Main DAG (`arxiv_paper_ingestion.py`)

**Schedule**: `0 6 * * 1-5` (Mon–Fri 6 AM UTC)  
**Max active runs**: 1  
**Catchup**: False

**Task graph**:

```
setup_environment → fetch_daily_papers → index_papers_hybrid → generate_daily_report → cleanup_temp_files
```

---

## 5. Task Logic (Bottom-Up)

### 5.1 Task 1: `setup_environment` (`setup.py`)

1. Call `get_cached_services()`
2. Verify DB: `SELECT 1` on PostgreSQL
3. Verify OpenSearch cluster health
4. Call `opensearch_client.setup_indices(force=False)` to ensure:
   - Hybrid search index exists (vector + keyword fields)
   - RRF (Reciprocal Rank Fusion) pipeline exists for hybrid queries

### 5.2 Task 2: `fetch_daily_papers` (`fetching.py`)

1. Compute target date: `execution_date - 1 day` (or yesterday)
2. Create `PipelineRun` row: execution_date, target_date, status='running'
3. `asyncio.run(run_paper_ingestion_pipeline(target_date, process_pdfs=True))`
4. Inside pipeline:
   - `arxiv_client.fetch_papers()` for metadata
   - Concurrent download + parse (semaphores: 5 downloads, 3 parses)
   - `metadata_fetcher._store_papers_to_db()` with parsed content; papers get `pipeline_run_id` FK
5. Update `PipelineRun`: papers_fetched, papers_stored, status='fetch_complete' → commit

### 5.3 Task 3: `index_papers_hybrid` (`indexing.py`)

1. Load `PipelineRun` for current execution_date (status = 'fetch_complete')
2. Load papers from PostgreSQL: `WHERE pipeline_run_id = ?` (or `created_at` in run window)
3. For each paper:
   - Chunk via `TextChunker` (600 words, 100 overlap)
   - Embed chunks with Jina AI
   - Index into OpenSearch hybrid index
4. Update `PipelineRun`: papers_indexed, chunks_created, chunks_indexed, status='index_complete' → commit

### 5.4 Task 4: `generate_daily_report` (`reporting.py`)

1. Load `PipelineRun` for current execution_date
2. Query PostgreSQL: total papers, count by date
3. Query OpenSearch: index stats (document count, size)
4. Build report from DB + OpenSearch, log it; optionally append to `pipeline_runs` or a reports table

### 5.5 Task 5: `cleanup_temp_files` (BashOperator)

Remove ephemeral PDFs in worker temp dir older than retention (e.g. 30 days). **Production**: PDF cache in object storage (S3/GCS) with lifecycle policies; no long-lived temp files on workers.

---

## 6. Data Flow Summary

```
arXiv API
    ↓
[fetch_daily_papers]
    ├── ArxivClient.fetch_papers()
    ├── ArxivClient.download_pdf() (concurrent)
    ├── PDFParserService.parse_pdf() (Docling)
    └── PaperRepository.upsert() → PostgreSQL
    ↓
[index_papers_hybrid]
    ├── Load papers from PostgreSQL
    ├── TextChunker.chunk_paper()
    ├── JinaEmbeddingsClient.embed_passages()
    └── OpenSearchClient.bulk_index() → OpenSearch
    ↓
[generate_daily_report]
    └── Aggregate stats from PipelineRun + PostgreSQL + OpenSearch
```

---

## 7. Design Decisions

| Decision                    | Rationale                                                    |
|-----------------------------|--------------------------------------------------------------|
| Reuse `src/` in DAGs        | Single source of truth, no code duplication                  |
| `@lru_cache` services       | Avoid reconnecting per task within same worker process       |
| Async in fetch/index        | Parallel downloads and embedding batches                     |
| **PostgreSQL for task data**| PipelineRun table: production standard, auditable, queryable |
| No XCom                     | XCom not for production handoff; DB is source of truth       |
| **CeleryExecutor / K8s**    | Production: horizontal scaling, fault isolation              |
| Idempotent tasks            | Retries and reruns safe; upsert, not duplicate inserts       |

---

## 8. Configuration (Production)

| Config                    | Source                                  |
|---------------------------|-----------------------------------------|
| `POSTGRES_DATABASE_URL`   | Secrets manager / K8s Secret (app DB)   |
| `AIRFLOW__DATABASE__SQL_ALCHEMY_CONN` | Separate Airflow metadata DB        |
| `OPENSEARCH_HOST`         | Service discovery (K8s service / LB)    |
| `JINA_API_KEY`            | Secrets manager                         |
| `AIRFLOW__CORE__EXECUTOR` | `CeleryExecutor` (or `KubernetesExecutor`) |

Secrets never in `.env` or image; injected at runtime. `PYTHONPATH` includes app source.

---

## 9. Interview Talking Points

1. **Why Airflow?** Scheduled ingestion, retries, observability, DAG-based orchestration. CeleryExecutor/K8sExecutor for scaling.
2. **Shared code**: DAGs orchestrate; business logic in `src/` reused by API and workers.
3. **Task handoff via PostgreSQL**: `PipelineRun` table — production standard, auditable, survives restarts. No XCom for data.
4. **Idempotency**: Upserts for papers; safe retries and backfills. Async in tasks for parallel downloads/embeddings.
5. **Hybrid search**: Keyword + vector, RRF in OpenSearch for semantic + lexical.
6. **Deployment**: CeleryExecutor or K8sExecutor; workers scale independently; non-root user in container.
7. **Fault tolerance**: Retries per task, individual paper failures don’t fail the run.
8. **Secrets & observability**: Credentials from secrets manager; metrics from pipeline_runs, Prometheus/Grafana.

---

## 10. Production Operations

| Area            | Practice                                                |
|-----------------|---------------------------------------------------------|
| **Monitoring**  | pipeline_runs for run stats; task duration, success rate |
| **Alerting**    | On DAG failure, task failure; Slack/PagerDuty integration |
| **Logging**     | Structured logs; ship to ELK/Datadog/CloudWatch          |
| **Secrets**     | Vault, AWS Secrets Manager, or K8s Secrets              |
| **Scaling**     | Celery workers; tune concurrency per task pool          |
| **Backfills**   | Idempotent tasks; use execution_date for date-scoped runs |

---

## 11. Quick Reference

| File / Component      | Purpose                                          |
|-----------------------|--------------------------------------------------|
| **DB tables**         | `pipeline_runs`, `papers` — see §3.3.1 for CREATE TABLE |
| `compose.yml` / K8s   | Dev: Compose; Prod: K8s manifests or managed (Astronomer, MWAA) |
| `airflow/Dockerfile`  | Airflow + deps + entrypoint; workers use same image |
| `airflow/entrypoint.sh` | Init, webserver, scheduler; workers run separately |
| `dags/arxiv_paper_ingestion.py` | Main DAG definition                      |
| `dags/arxiv_ingestion/common.py` | Service wiring with caching               |
| `dags/arxiv_ingestion/setup.py`  | Env verification and index setup         |
| `dags/arxiv_ingestion/fetching.py` | Fetch, parse, store to PostgreSQL       |
| `dags/arxiv_ingestion/indexing.py` | Chunk, embed, index to OpenSearch       |
| `dags/arxiv_ingestion/reporting.py` | Aggregate and log pipeline stats       |
| `src/services/metadata_fetcher.py` | Fetch + parse + store orchestration     |
| `src/services/indexing/hybrid_indexer.py` | Chunk, embed, index logic          |
