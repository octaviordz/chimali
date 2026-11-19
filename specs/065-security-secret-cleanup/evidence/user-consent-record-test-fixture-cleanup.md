# User consent record test fixture cleanup

`UserConsentRecordTest` constructs consent records that each take independent mutable ownership of RP text. The test now tracks every successfully created record and erases each owner in `@AfterTest`; it also releases fixture references. A dedicated regression retains a borrowed RP view, runs teardown, and verifies that all characters read as zero afterward. Invalid records still erase their owner in the production constructor's rejection path.

Validation: the focused consent test reports pass 28 test cases across six XML reports with zero failures/errors. The complete FIDO Android host suite passes 1,305 tests across 172 XML reports with zero failures/errors/skips, and `:feature:fido2:ktlintCheck` passes. This verifies synthetic host fixtures only; immutable identity strings, production consent consumers, serialization/database copies, heap reachability and platform behavior remain open.
