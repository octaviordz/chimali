# Data Model: Lint Remediation

**Branch**: `040-lint-remediation` | **Date**: 2026-05-15

## No Data Model Changes

This feature is a code quality remediation that modifies inline annotations and exception handling patterns. It does not introduce, modify, or remove any:

- Database tables or columns
- Domain entities or value objects
- Data transfer objects
- Serialization schemas
- Event sourcing event types

All changes are confined to:
1. Removing `@Suppress` annotations
2. Refactoring `catch` blocks to use specific exception types
3. Replacing `TODO:` markers with `DEFERRED(040):` references
4. Adding a small SDK-gated extension function for deprecated `Intent.getParcelableExtra`
