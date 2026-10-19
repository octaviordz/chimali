# Independent HDK fixture generator

This isolated Python tool creates deterministic, synthetic expected values for the local profile. It does not import Chimali code or consume user wallet data. Run it with Python 3.11 or newer in a disposable virtual environment after installing the exact versions in `requirements.txt`:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r tools/hdk-reference/requirements.txt
.\.venv\Scripts\python.exe tools/hdk-reference/generate-fixtures.py
.\.venv\Scripts\python.exe tools/hdk-reference/generate-fixtures.py --check
```

The generator first checks RFC 5869 test case 1, the RFC 9180 Appendix A.3.1 DHKEM(P-256) deterministic key pair, the RFC 8032 Ed25519 test vector, and the NIST ACVP ML-DSA-65 internal key-generation vector. It writes `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json` and a cross-engine ML-DSA signature fixture at `feature/fido2/src/androidHostTest/resources/hdk/mldsa65-cross-engine-v1.json`; `--check` performs read-only comparisons for both.

Use `--check` for a read-only reproducibility run. It performs the same independent checks, regenerates every synthetic value in memory, and fails if the structured output differs from the frozen fixture. Run without `--check` only when intentionally refreshing the fixture and its provenance.

P-256 child arithmetic is implemented directly with Python integers and independently reconstructed with `cryptography`'s OpenSSL-backed P-256 implementation. Ed25519 public keys use `cryptography`. ML-DSA-65 uses `dilithium-py`'s deterministic FIPS 204 internal key-generation and signing entry points. The checked ACVP input is the ML-DSA-65 seed `A991FD42B071D49C48AE3E75C647459E0DAAD1E1BA356A04801912D3294BCFF8`; public and secret key SHA-256 values must match the NIST vector before the independent signature fixture is generated and verified.

The ML-DSA package is an independent fixture oracle only. It is not a production dependency or an endorsement of that package for deployment. The latest regeneration used Python 3.14.7 and OpenSSL 3.5.7 through `cryptography` 50.0.2. The ACVP source is `https://github.com/usnistgov/ACVP-Server/blob/975de31eb83d87039ec88934fdc47d8c312b892d/gen-val/json-files/ML-DSA-keyGen-FIPS204/internalProjection.json` (commit `975de31eb83d87039ec88934fdc47d8c312b892d`). Record runtime/backend versions and preserve the generated JSON with review evidence when refreshing fixtures.

These are synthetic expected results, not security signoff. The exact application composition remains subject to the independent review gate in `specs/055-migrate-hdk-draft/security-review-packet.md`.
