# CTAP timeout test fixture cleanup

`Ctap2TimeoutPropagationTest` creates synthetic registration and assertion requests. Test teardown now clears the caller-owned challenge, client-data hash and user-ID byte inputs, erases the RP and user entities, and erases the separately owned RP IDs retained by each `GetAssertionOptions`. A direct regression retains the mutable request inputs, invokes teardown, and verifies the buffers contain only zero bytes.

Validation: all 7 focused timeout-propagation cases pass; the full FIDO host suite passes 1,307 tests across 172 XML reports with zero failures/errors/skips; and `:feature:fido2:ktlintCheck` passes. This covers only synthetic host fixtures; it does not prove platform/provider copies or production request lifetime, database, heap, or device behavior.
