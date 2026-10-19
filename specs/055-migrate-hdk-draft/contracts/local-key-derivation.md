# Contract: Chimali Local Credential Derivation v1

**Date**: 2026-10-14  
**Status**: Exact design proposed for implementation; subject to the security gate in [security-review.md](../security-review.md).  
**Scope**: Fresh installations; current local keys only. This contract defines an application policy for secret derivation, separate from OpenID4VCI issuance.

## Encodings and root

- ASCII labels below have exactly the displayed bytes, without a terminator except the explicit `0x00` in `credentialInfo`.
- `I2OSP` is fixed-width unsigned big-endian. Concatenation is byte concatenation.
- `C` is the complete decoded credential ID, 16–1023 bytes. Text representations are canonical Base64URL without padding; equivalent allowed padded input decodes to the same `C`.
- `S` is the full 64-byte BIP39 PBKDF2-HMAC-SHA512 result. The current wallet generates/imports 24 English words and uses an empty BIP39 passphrase. No passphrase UI is introduced.
- `HKDF-Extract` and `HKDF-Expand` use SHA-256 and [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html).

```text
root_salt = ASCII("chimali-local-key-derivation-v1")
PRK = HKDF-Extract(root_salt, S)                         # 32 bytes

credentialInfo(label, C) =
    ASCII(label) || 0x00 || I2OSP(length(C), 2) || C
```

The fixed salt is public domain separation, not fresh entropy. `PRK` is the wallet derivation root returned as an owned copy by `getMasterSeed`; it is not a truncation of `S`. Erase `S` after root extraction and required current provisioning. Existing non-signature key consumers receive PRK copies and retain their independently documented KDF policies; they must erase copies.

## Supported algorithm/profile pairs

| Algorithm in current application | Profile identifier | HKDF expansion label | Output |
| --- | --- | --- | --- |
| ES256 / P-256 | `chimali-local-hdk-p256-v1` | `chimali-local-hdk-p256-v1/credential-secret` | 32-byte local secret |
| Ed25519 | `chimali-local-ed25519-v1` | `chimali-local-ed25519-v1/credential-seed` | 32-byte seed |
| ML-DSA-65 | `chimali-local-mldsa65-v1` | `chimali-local-mldsa65-v1/credential-seed` | 32-byte `xi` |

All three use `HKDF-Expand(PRK, credentialInfo(label, C), 32)`. The application retains its current COSE algorithm mapping; profile validation is an explicit match against those supported algorithm cases. Unknown combinations fail; there is no fallback to ES256.

RP identity, account display text, labels, timestamps, and signature counters are not derivation inputs. Mutating them must not change a key. A stable per-wallet parent needs no stored parent registry.

## Deterministic P-256 parent

```text
parent_ikm = HKDF-Expand(
    PRK,
    ASCII("chimali-local-hdk-p256-v1/parent"),
    32)

(sk_parent, pk_parent) = RFC9180.DeriveKeyPair(parent_ikm)
```

Use exactly [RFC 9180 §7.1.3](https://www.rfc-editor.org/rfc/rfc9180.html#section-7.1.3) with DHKEM(P-256, HKDF-SHA256), KEM identifier `0x0010`. Retain standard `HPKE-v1`, suite `"KEM" || I2OSP(0x0010, 2)`, `dkp_prk` and `candidate` labeling. The bounded sampler evaluates counters 0–255 inclusive and accepts only `0 < sk_parent < n`. Exhaustion fails; invalid scalar input is never repaired with modulo reduction.

This use is deterministic parent generation only. It does not introduce HPKE issuance, encapsulation, decapsulation or remote key handles.

## Local child-key relation adapted from the draft

Section 7 of [draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/) defines `bk` as the per-issuance X25519 `shared_secret` and `ctx` as the parent public key plus credential index. This local profile deliberately does not implement that exchange. It substitutes the deterministic per-credential `local_secret` from the RFC 5869 branch above and uses the local deterministic software parent. The draft's §§3 and 7 provide the P-256 scalar mapping and multiplicative child-key relation only; they do not define Chimali's root, parent provisioning, credential-ID input, independent algorithm branches, storage, or recovery. This input substitution is a local custom protocol and does not interoperate with or inherit the security analysis of the draft's issuance flow.

The local adaptation applies that scalar mapping and relation as follows:

```text
n = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
i = 0                                                  # local FIDO2 leaf
ctx = SEC1_UNCOMPRESSED(pk_parent) || I2OSP(i, 4)
digest = SHA-256(local_secret || ctx)
b = (OS2IP(digest) mod (n - 1)) + 1
pk_child = ScalarMult(pk_parent, b)
sk_child = sk_parent * b mod n
```

Canonical public encoding is `0x04 || x32 || y32`, 65 bytes. The low-level child operation accepts the full `UInt` range; only the FIDO2 application policy fixes `i` to zero. The child preimage directly concatenates the local secret and context; there is no separator byte between them. No old ID prefix, XMD expansion, extra hash-to-field stage, or salt evolution enters this construction. Its specified small scalar bias is retained; do not substitute another mapping under this identifier.

The relation permits chaining child calls mathematically, but this feature has no production hierarchy/fold API. Direct composition fixtures establish the relation without retaining unused tree machinery.

## Signing and key encodings

- P-256 signs with ordinary SHA-256 ECDSA using the derived child scalar in the current software provider boundary. Do not implement raw-parent ECDSA transformation or claim hardware-preserving signing.
- Ed25519 imports its 32-byte seed according to RFC 8032.
- ML-DSA-65 imports `xi` through BC 1.80 `MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, xi)` and obtains the matching public key from `MLDSAPrivateKey.getPublicKey()`. The custom randomness adapter is removed.
- Public generation output remains native P-256 SEC1/Ed25519 raw or current ML-DSA public encoding as appropriate. Stored Java/JCA public keys remain X.509 encoding. A single explicit adapter converts these formats; constant-time equality compares the same normalized representation.
- CTAP registration public-key encoding, self-attestation signing and assertion signing use the same explicit descriptor. ECDSA signature bytes need not be deterministic; their verification must be correct.

## Boundary validation and ownership

P-256 public inputs must be exactly 65 bytes, uncompressed, finite, on the specified curve, with valid field coordinates. Received parent/private/factor scalars must be exactly 32 bytes and in `1..n-1`. If a key pair is supplied together, verify correspondence. Serialize scalars to exactly 32 unsigned bytes without truncating invalid values. Require a 32-byte local secret and valid credential/profile inputs.

Input buffers are borrowed for the duration of the operation unless the API explicitly transfers ownership. Do not mutate borrowed caller storage. Operation owners erase full BIP39 material, PRK copies, parent input/scalar copies, credential seeds, digest/factor/child bytes and private encodings in `finally`; returned secret buffers acquire a new owner. Public output carries no secret seed/factor. Provider caches own separate arrays and have explicit session destruction.

The X25519 all-zero-output rule belongs to the excluded exchange. Do not invent a rejection rule for an all-zero HKDF output or confuse it with an invalid zero scalar: the draft mapping already yields a non-zero factor.

## Independent fixtures

Implement the reference outside Chimali and without its production BC engine. First check HKDF against RFC 5869 vectors and the parent sampler against RFC 9180 P-256 vectors. Freeze provenance, dependency versions and expected bytes.

- Direct child family: parent scalar 1; synthetic secret bytes `00` through `1f`; indices 0, 1, `0x80000000`, `0xffffffff`; include preimage, digest, factor, child scalar and child public point. Verify via independent P-256 arithmetic and OpenSSL-backed public-key reconstruction.
- Local family: synthetic `S` bytes `00` through `3f`, plus standard BIP39 input vectors; credential lengths 16, 32 and 1023; include root PRK, parent input, accepted candidate counter, all algorithm seeds and P-256 public/private relationship.
- Collision family: two published fixture IDs sharing the retired mapping's four-byte hash prefix, with differing full identity inputs and independently established distinct current outputs.
- Signing family: verify registration/assertion signatures against independently imported public keys; changing the root, full identifier, algorithm label, parent or message must affect the expected result or cause rejection. Do not use the production function as its own expected-value oracle.

## Conformance statement

Use: “Chimali's local P-256 profile adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00`, substituting a separately specified RFC 5869 credential secret for the draft's per-issuance X25519 shared secret. This local profile does not implement the draft's OpenID4VCI issuance protocol or claim interoperability with it.”

No OpenID4VCI capability is advertised by this feature. Parent assurance is the current software-wallet authorization and encrypted recovery-storage policy; issuer assurance and non-exportable-parent claims are outside this contract. The exact composition still requires the recorded implementation security review.
