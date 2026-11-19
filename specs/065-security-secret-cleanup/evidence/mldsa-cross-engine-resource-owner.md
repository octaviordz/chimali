# ML-DSA cross-engine fixture resource owner review

**Date:** 2026-11-17  
**Requirements:** FR-002/003/004/010; T005/T006/T012/T015.

The Android host-test resource `mldsa65-cross-engine-v1.json` contains a deterministic ML-DSA-65 seed from the cited NIST ACVP internal key-generation vector, plus a synthetic message and signature. It is consumed only by `PostQuantumCryptoTest` in the `androidHostTest` source set and is not an Android application resource. The test decodes the seed, message, and signature into mutable arrays and clears those arrays in `finally`; the JSON text and parsed immutable string properties remain managed by the host test runtime until collection. The resource is mapped as a Relevant test fixture under `CLS-KEY-005`. This is a source-owner disposition: it does not establish test-process heap erasure or provider-key object cleanup, and it does not claim the vector is suitable as an application secret.

Resource SHA-256: `0c9b0a202ac28f0253abacbeedcabcabd54fc264d907e0f3d89ccef8354ae044`  
Consumer SHA-256: `5caee30101fd9a27ed8e1e5bd15de444088e1d5d74cb05d7f96e3382313fa2f8`.
