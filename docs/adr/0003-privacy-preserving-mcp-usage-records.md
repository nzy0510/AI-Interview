# Privacy-Preserving MCP Usage Records

Status: accepted

InterWise records public MCP usage for quota enforcement, abuse detection, and operational debugging, but it does not store raw user query text. Usage records may keep identifiers, tool names, query hashes or lengths, result counts, latency, status, and hashed request metadata, preserving enough signal to operate the service without turning MCP logs into prompt transcripts.
