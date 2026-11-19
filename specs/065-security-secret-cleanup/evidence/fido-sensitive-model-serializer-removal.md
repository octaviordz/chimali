# FIDO sensitive-model generic serializer removal

Date: 2026-11-18  
Finding: SEC065-CORE-056  
Scope: `SensitiveText`, `RelyingParty`, `UserConsentRecord`, and `CredentialSummary`

## Reachability finding and correction

Kotlinx serialization encodes primitive strings through `Encoder.encodeString(String)`. The `SensitiveTextSerializer` therefore materialized an immutable `String` from its temporary character copy even though the temporary array was erased. `RelyingParty`, `UserConsentRecord`, and `CredentialSummary` exposed generated serializers over those sensitive owners. Production search found no serializer call sites; the only references were generic model compatibility tests.

Removed the generic serializer and serializable annotations from `SensitiveText` and the three FIDO identity models. Removed the serialization-only tests that exercised this String-producing boundary. Production persistence continues through the dedicated mutable-byte and sensitive-field codecs; enum serialization remains available where it carries no user text.

## Verification and limits

Core-domain tests pass (103 tests across 23 reports) and the FIDO Android host suite passes (1,349 tests across 175 reports), with zero failures, errors, or skips. Both domain and FIDO KtLint checks pass. Production source search confirms there are no remaining direct references to the removed serializer APIs.

This is a source API change for callers that used generic serialization of these domain objects. It does not claim that platform libraries or callers outside this repository never materialize immutable Strings at separately approved display, protocol, or persistence boundaries.
