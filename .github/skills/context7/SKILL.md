---
name: context7
description: Look up current API for project libraries (Testcontainers, Fabric8, avaje, Picocli, JUnit 5) BEFORE writing code. Use proactively during implementation planning to verify method signatures and find idiomatic examples — not only after hitting a compile error.
---

# Context7 as source of up-to-date code documnentation

Always use Context7 when I need library/API documentation, code generation, setup or configuration steps without me having to explicitly ask.

Context7 is an MCP server you've got at your disposal with following tools:

resolve-library-id: Resolves a general library name into a Context7-compatible library ID.

    query (required): The user's question or task (used to rank results by relevance)
    libraryName (required): The name of the library to search for

query-docs: Retrieves documentation for a library using a Context7-compatible library ID.

    libraryId (required): Exact Context7-compatible library ID (e.g., /mongodb/docs, /vercel/next.js)
    query (required): The question or task to get relevant documentation for

