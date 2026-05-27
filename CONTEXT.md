# InterWise

InterWise is an AI mock interview product with a single product runtime and a developer-maintained question bank. This context records the domain language that current code and documentation should preserve.

## Language

**InterWise**:
The product that provides mock interviews, resume analysis, review reports, operations controls, and developer-managed interview knowledge.
_Avoid_: AI-Interview when referring to the product name.

**Main App**:
The user-facing web application that owns registration, login, interviews, resumes, reports, Settings, quota enforcement, and developer operations.
_Avoid_: Backend-only app, old project.

**Question Bank**:
The curated interview knowledge collection shared by interview RAG retrieval and developer maintenance workflows.
_Avoid_: Vector database, external service database.

**Knowledge Atom**:
The smallest importable and retrievable question-bank unit, with answer guidance, classification, difficulty, tags, and publication state.
_Avoid_: Raw document chunk.

**Published Atom**:
A knowledge atom that is available for interview retrieval.
_Avoid_: Any atom, draft atom.

**RAG Retrieval**:
The process of selecting relevant published atoms from the question bank to ground interview follow-up questions.
_Avoid_: DeepSeek memory, direct vector-store ownership.

**Question Bank Maintenance Skill**:
The local workflow that prepares and reviews JSON import packages from source material. It generates artifacts only and does not publish content.
_Avoid_: Server, API client, publishing service.

**Import Package**:
The structured JSON package produced from source material before validation and publication.
_Avoid_: Direct database patch, Qdrant payload.

**Developer Admin Console**:
The developer-only Settings panel used to upload an import package, supply `APP_ADMIN_TOKEN`, validate, dry-run, publish, search, archive, and maintain indexing.
_Avoid_: Public administration endpoint, script submission.

**Developer Account**:
A trusted InterWise account that can see developer operations while still requiring the configured admin token for protected management actions.
_Avoid_: Root user, unlimited account.

**AI Usage Quota**:
The website quota that protects cost-bearing AI workflows such as interviews, resume analysis, reports, and Mentor generation.
_Avoid_: Authorization.

**Technical Interview Stage**:
The interview phase that evaluates role-specific engineering knowledge, project depth, architecture trade-offs, and implementation reasoning.

**HR Soft-Skill Stage**:
The interview phase that evaluates behavioral, communication, motivation, pressure-handling, collaboration, and career-planning signals through a dedicated HR question-bank category.

## Relationships

- **InterWise** contains one **Main App**.
- The **Main App** owns users, interviews, Settings, developer operations, question-bank publication, and RAG retrieval.
- The **Question Bank** contains many **Knowledge Atoms**; only **Published Atoms** participate in **RAG Retrieval**.
- The **Question Bank Maintenance Skill** produces an **Import Package** from source material for human review.
- The **Developer Admin Console** is the only publication surface for generated packages and requires both a **Developer Account** and `APP_ADMIN_TOKEN`.
- MySQL is the source of truth for question-bank business state; Qdrant is a rebuildable retrieval index.
- The **Technical Interview Stage** uses role-specific technical categories; the **HR Soft-Skill Stage** uses its dedicated category.

## Invariants

- Scripts and Skills may generate and locally validate import packages, but they must not publish atoms directly.
- Question-bank publishing, reindexing, archival, and protected search are performed through the **Developer Admin Console**.
- Secrets such as `APP_ADMIN_TOKEN`, JWT signing keys, SMTP credentials, and model API keys must stay in environment configuration and logs must not expose them.
- Publication must validate the package before writing live atoms; only published atoms may be synchronized into interview retrieval.
- External MCP endpoints, MCP user tokens, MCP quota processing, and an MCP deployment service are not part of the current InterWise product boundary. Retired MCP tables may remain as read-only historical records.

## Decision Records

- [ADR 0002: Question Bank Import Lifecycle](docs/adr/0002-question-bank-import-lifecycle.md)
- [ADR 0004: Remove MCP Feature From InterWise](docs/adr/0004-remove-mcp-feature.md)
