# Public credential-key decoder owner scope

Date: 2026-11-15
Finding: SEC065-CORE-027

`PublicKeyDecoder` accepts a stored Base64 X.509 public-key value and COSE algorithm, decodes a temporary byte array, creates an `X509EncodedKeySpec`, and transfers the provider `PublicKey` through the repository's `Outcome`. The production path handles public credential metadata, not a private key or secret scalar. It is associated with a stored credential and is therefore mapped under `CLS-FIDO-001`; diagnostics must not expose the credential-bound value or provider error details. Temporary public-key bytes/spec/provider copies are not secret-zeroization targets, but caller/object lifecycle and associated-record release remain part of the broader credential owner review.

`PublicKeyDecoderTest` generates synthetic keypairs for ML-DSA, EC and Ed25519; it passes only each public-key encoding to the decoder. The generated private-key objects are not explicitly destroyed in the existing test methods and remain provider/JVM managed. This is recorded as a test-owner limitation, not as production private-key leakage. The class was not run during this inventory-only pass.

This source review does not assess Android provider implementations, public-key object lifetime after repository use, or heap behavior.
