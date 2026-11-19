# Removed unused credential-parameter salt API

Date: 2026-11-15
Finding: SEC065-CORE-028

## Change

Removed `PublicKeyCredentialParameters.salt`, its size validation/constants, custom salt-derived equality/hash logic, and `getSafeSalt()`. The latter returned caller-owned salt aliases when supplied and generated a predictable 16-byte sequence when absent. Repository-wide production source search found no reader or constructor passing salt; current production callers use the type/algorithm/curve/keyType negotiation fields only. Tests were the only callers of the salt API.

Added `obsolete salt buffer API is absent from algorithm metadata` to `PublicKeyCredentialParametersTest`. It failed before the removal because the declared `salt` property remained, then passed after removal. Updated tests to retain algorithm validation/factory/equality coverage without manufacturing the unused salt buffer.

## Verification

The full `:feature:fido2:testAndroidHostTest` suite passes 1,255 tests across 167 XML reports with zero failures, errors or skips. `:feature:fido2:ktlintCheck` passes. KtLint initially identified an extra blank line in the edited model; that formatting issue was corrected before the passing KtLint run.

## Limits

The model remains active as public algorithm-negotiation metadata; only the dead salt-bearing API was removed. This does not close other request-array owners, PRF salt lifecycle, provider copies, heap roots, or device/runtime requirements.
