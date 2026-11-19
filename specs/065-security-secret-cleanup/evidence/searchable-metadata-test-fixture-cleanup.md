# Searchable metadata test fixture cleanup

**Finding:** SEC065-FIDO-009  
**Scope:** Synthetic lookup-token arrays and in-memory SQLite driver in `CredentialRepositorySearchableMetadataTest`.

The repository test tracked mutable synthetic lookup tokens returned by its metadata-service mock, but only cleared the tracking list; arrays could remain populated if an assertion failed before the local assertion helper ran. An `@AfterTest` cleanup now zeroes every tracked lookup-token array, clears the list, and closes the in-memory JDBC driver for every case.

The full FIDO host suite passes 1,271 tests across 168 XML reports with zero failures/errors/skips. The searchable metadata class passes 17 cases; the separate cleanup-owner test passes one. KtLint passes and Detekt reports zero findings. XML SHA-256 values: `8a6a662428cdef0b41eebc6f25507a23cd19cc77e3811194bf88860e779615ce` (repository metadata suite) and `6d57416f2a01eed5a49f85b43f2f6c86944073acd216e680cf79d55746366e7f` (fixture teardown assertion).

This closes test-instance fixtures only; production metadata providers, Android SQLDelight/JDBC page copies and heap roots remain open under T005/T006/T015.
