# Searchable metadata query test ownership map

**Date:** 2026-11-15  
**Scope:** `SearchableMetadataDaoTest`, a JDBC host integration test for SQLDelight token-index queries. There is no production `SearchableMetadataDao` class; production writes and indexed reads are implemented by the credential and consent DAOs.

The nine-case class checks exact-match credential and consent queries, token counts, deletion, a combined RP/user lookup, display search combined with exact RP filtering, and null-token behavior. It uses synthetic IDs, RP/user bytes and lookup tokens in an in-memory JDBC database. Its `@AfterEach` erases tracked synthetic byte arrays and retained query rows, re-queries and erases remaining credential/consent row buffers, asserts those buffers are zeroed, and closes the JDBC driver. The focused nine-case run passes; the full FIDO host suite passes 1,247 tests across 166 reports with zero failures, errors, or skips. FIDO KtLint passes.

These assertions cover mutable arrays retained by the test and mutable row buffers returned by the in-memory JDBC driver before it closes. They do not establish production DAO cleanup, encrypted-driver behavior, SQLDelight bind-copy erasure, persistent database-page cleanup or heap-root absence. Those remain open under T005/T006/T012/T015.
