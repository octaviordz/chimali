# Generated SQLDelight credential-row ownership review

**Date:** 2026-11-17
**Finding:** SEC065-DB-001 follow-up; T006/T012

The current generated `Passkey_credential` row is a Kotlin data class. Its nullable and required mutable `ByteArray` fields include encrypted metadata, RP ID and lookup index, RP name, user display name, user ID and lookup index, and user name. Its compiler-generated `copy()` preserves array references; a copied row is not an independently erasable owner. Repository search found no production copying of `Passkey_credential` rows. Test fixtures do use generated `copy()` for mutations, so they must be treated as aliases and not independently cleaned while the source fixture remains live.

Current source paths consume rows directly: `PasskeyCredentialEntity.toDomainModelWithTextDecoder` erases row metadata in `finally`, summary/read batches erase each source row in their terminal `finally`, and destructive batch cleanup erases accepted rows after use. Rejected model/flow results have separate owner cleanup. No new reachable application leak from generated `copy()` was found in this pass.

Open boundaries remain: SQLDelight driver/native materialization and caches, transaction/page/query results outside the reviewed callers, immutable String fields (`id`, `credential_id`, `aaguid`, `derivation_profile`, `public_key`, and optional label), durable SQLite/WAL/backup state, provider copies, caller completion, and heap roots. This review does not claim physical/database-page erasure.

Evidence hashes:

- Generated `Passkey_credential.kt`: `713d1e80b5837028a3a6d05a63bcc16c858b507a70577624fd5ccf2c359f51f2`
- Schema/query source `Fido2Database.sq`: `df6a7035a8748f76f93b88212e76481752a51d91dc7c78961e493c4c2d3ddeff`
- Mapper source: `9d21702fbd921b43925b560e690453842e0e0145d638c74cff5167cb2d10fb8a`
- Read operations source: `c24eabe66f29774731d2c1030d17eb1f1bc4577f9bbadea33dca8c3b71b21e6f`
- DAO source: `1b563f4b204595d07488999ead358cf4a3b538bba48704ba8b40d0f81d094183`
