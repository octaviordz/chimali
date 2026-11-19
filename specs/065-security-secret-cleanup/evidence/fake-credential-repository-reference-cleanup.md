# Fake credential repository reference cleanup

**Finding:** SEC065-CORE-036  
**Date:** 2026-11-16  
**Scope:** `FakeCredentialRepository` test-only in-memory credential map

The fake stored `Credential` objects in a mutable map and had no cleanup operation. It now exposes `clear()`, and `CredentialRepositoryTest` invokes it in `@AfterTest`. A synthetic regression saves a credential, clears the fake, and asserts no credential remains available through `getAllCredentials()`.

The full `:core:domain:testAndroidHostTest :core:domain:ktlintCheck --no-daemon` run passes 104 tests across 24 reports with zero failures/errors; KtLint passes. `CredentialRepositoryTest.xml` SHA-256: `9142568BF9317BD2D9E151CC55EABF7A0AA0E4986AB1A65679004969E805CC1F`.

This removes map-held references. `Credential` contains immutable Strings and this cleanup does not erase those values or any copies held by test results; they remain GC-managed. The fake is test-only and is not used by production code.
