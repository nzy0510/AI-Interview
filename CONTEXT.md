# InterWise

InterWise is an AI mock interview product with one product runtime and one external question-bank access surface. This context records the domain language for the main app, the question bank, and the standalone MCP service so future implementation and documentation keep the same boundaries.

## Language

**InterWise**:
The product that provides mock interviews, resume analysis, review reports, operations controls, and external question-bank access.
_Avoid_: AI-Interview when referring to the product name.

**Main App**:
The user-facing web application that owns registration, login, interviews, resumes, reports, Settings, quota enforcement, and developer operations.
_Avoid_: Backend-only app, old project.

**Question Bank**:
The curated interview knowledge collection shared by interviews, RAG retrieval, Skills, and MCP clients.
_Avoid_: MCP database, vector database.

**Knowledge Atom**:
The smallest importable and retrievable question-bank unit, with prompt material, answer guidance, classification, difficulty, tags, and publication state.
_Avoid_: Raw question, document chunk.

**Published Atom**:
A knowledge atom that is available for interview retrieval and public MCP search.
_Avoid_: Any atom, draft atom.

**RAG Retrieval**:
The process of selecting relevant published atoms from the question bank to ground interview follow-up questions and external MCP responses.
_Avoid_: DeepSeek memory, database search only.

**Standalone MCP Service**:
The independent service that exposes question-bank tools to external AI clients without depending on the Spring Boot runtime code.
_Avoid_: Embedded MCP, backend MCP controller.

**Public MCP Endpoint**:
The `/mcp` endpoint for ordinary MCP clients, limited to read-only and desensitized question-bank access.
_Avoid_: Admin MCP, full question-bank API.

**Public MCP Toolset**:
The ordinary-user MCP tools for read-only search, summary retrieval, category browsing, context generation, and usage-status checks.
_Avoid_: Import tools, publishing tools, reindex tools.

**Admin MCP Endpoint**:
The local-only `/mcp-admin` endpoint for trusted maintenance workflows such as import validation, import submission, full atom lookup, and reindexing.
_Avoid_: Public MCP, user MCP, public admin route.

**Admin MCP Toolset**:
The trusted maintenance MCP tools for import validation, import submission, full atom inspection, and question-bank reindexing.
_Avoid_: Ordinary user tools, public tools.

**Question Bank Maintenance Skill**:
The local workflow client that prepares question-bank import packages and may call trusted maintenance tools.
_Avoid_: MCP service, server.

**Source Material**:
The original learning or interview-preparation material used to produce question-bank content.
_Avoid_: Knowledge atom, vector record.

**Import Package**:
The structured package produced from source material before validation and submission to the question bank.
_Avoid_: Direct database patch, Qdrant payload.

**User MCP Token**:
A token generated from Settings for exactly one InterWise user, granting public MCP access and stored server-side only as a hash.
_Avoid_: Admin token, developer token, team token, shared token.

**Question Bank Admin Token**:
The environment-controlled secret for trusted question-bank maintenance workflows.
_Avoid_: User token, Settings token.

**Developer Account**:
A trusted InterWise account that can access developer operations and receive maintenance-scoped exemptions or higher quotas while still respecting endpoint authorization and audit boundaries.
_Avoid_: Root user, unlimited account, admin token holder.

**Operations**:
The Settings-area view for developer-only statistics, behavior records, quota controls, and MCP token management.
_Avoid_: Public admin page, left-nav admin module.

**Quota**:
The per-user or per-token usage boundary that protects AI cost, MCP traffic, and abuse-sensitive workflows.
_Avoid_: Permission, authorization.

**AI Usage Quota**:
The website quota that protects cost-bearing AI workflows such as interviews, resume analysis, reports, and Mentor generation.
_Avoid_: MCP quota.

**MCP Usage Quota**:
The external-client quota that protects public MCP search and retrieval traffic.
_Avoid_: AI interview quota.

**MCP Usage Record**:
A privacy-preserving record of a public MCP call used for quota enforcement, abuse detection, and operational debugging.
_Avoid_: Query transcript, conversation log.

**Desensitized Result**:
A public MCP response that exposes enough answer guidance for learning or retrieval but avoids returning the full raw atom payload.
_Avoid_: Full answer dump, source record.

**Reference Answer Summary**:
The concise, non-authoritative answer guidance returned to ordinary users or public MCP clients.
_Avoid_: Standard answer, official answer.

## Relationships

- **InterWise** contains one **Main App** and one **Standalone MCP Service**.
- The **Main App** owns users, interviews, Settings, **Operations**, and **User MCP Token** lifecycle.
- The **Question Bank** contains many **Knowledge Atoms**.
- Only **Published Atoms** participate in **RAG Retrieval** and **Public MCP Endpoint** results.
- The **Public MCP Endpoint** accepts **User MCP Tokens**, exposes the **Public MCP Toolset**, returns **Desensitized Results**, and writes **MCP Usage Records**.
- The **Public MCP Toolset** may search published atoms, retrieve reference summaries, list categories, generate interview context, and report usage status.
- The **Public MCP Toolset** must not import content, publish atoms, reindex the question bank, or return full atom payloads.
- A **Desensitized Result** may include a **Reference Answer Summary**, but it must not expose the full **Knowledge Atom** payload.
- The **Admin MCP Endpoint** accepts the **Question Bank Admin Token**, exposes the **Admin MCP Toolset**, may operate on draft or full atom data, and is accessed locally rather than through the public site route.
- The **Question Bank Maintenance Skill** is a client workflow; it may generate import packages and call the **Admin MCP Endpoint**, but it does not own the server boundary.
- **Source Material** becomes an **Import Package**, then passes validation before creating or updating **Knowledge Atoms**.
- **Knowledge Atoms** become visible to **RAG Retrieval** and the **Public MCP Endpoint** only after publication and reindexing.
- A **User MCP Token** belongs to exactly one InterWise user; InterWise does not currently model team-shared MCP access.
- A **Developer Account** may bypass or receive higher **Quota**, but it does not automatically turn a **User MCP Token** into the **Question Bank Admin Token**.
- A **Developer Account** exemption is for development, maintenance, and acceptance testing; it should not remove authorization checks or audit records.
- **Quota** limits usage volume; endpoint tokens and account roles decide authorization.
- **AI Usage Quota** and **MCP Usage Quota** are separate quota pools and should not consume from each other.
- An **MCP Usage Record** may store identifiers, tool name, query hash or length, result count, latency, status, and hashed request metadata; it must not store raw user query text.

## Example dialogue

> **Dev:** "Should the Settings-generated token let my Claude client reindex the **Question Bank**?"
> **Domain expert:** "No. A **User MCP Token** only calls the **Public MCP Endpoint** and receives **Desensitized Results** from **Published Atoms**. Reindexing belongs to the **Admin MCP Endpoint** with the **Question Bank Admin Token**."

## Flagged ambiguities

- "MCP" was used to mean the data, the service, and the endpoint. Resolved: use **Question Bank** for the data, **Standalone MCP Service** for the service, **Public MCP Endpoint** or **Admin MCP Endpoint** for the route.
- "Admin" was used for both developer website access and full question-bank maintenance. Resolved: use **Developer Account** for the InterWise account role and **Question Bank Admin Token** for maintenance authorization.
- "Admin MCP" could imply a public management route. Resolved: the **Admin MCP Endpoint** is local-only and not part of the public website surface.
- "MCP tools" could imply every tool is available to ordinary users. Resolved: **Public MCP Toolset** is read-only and desensitized; **Admin MCP Toolset** is local-only maintenance.
- "Standard answer" would over-promise uniqueness and authority for interview questions. Resolved: public-facing answer guidance is a **Reference Answer Summary**.
- "Skill" was used as if it were the MCP service. Resolved: the **Question Bank Maintenance Skill** is a client workflow that uses service endpoints; it is not the service itself.
- "Adding to the question bank" could imply direct database or Qdrant edits. Resolved: new content follows **Source Material** to **Import Package** to validation to **Knowledge Atom** to publication and reindexing.
- "MCP token" could imply a team or project credential. Resolved: a **User MCP Token** is scoped to one InterWise user; team-shared tokens are out of scope for now.
- "Quota" was used for both website AI cost controls and public MCP traffic controls. Resolved: **AI Usage Quota** and **MCP Usage Quota** are separate pools.
- "Developer account" could imply unrestricted root access. Resolved: a **Developer Account** only receives development and maintenance-oriented exemptions or higher quotas; authorization and audit still apply.
- "Usage log" could imply storing user prompts. Resolved: **MCP Usage Records** are operational records and must not store raw query text.
