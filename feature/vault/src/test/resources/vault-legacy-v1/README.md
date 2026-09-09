# Vault v1 compatibility fixtures

T061 / FR-VAULT-035. These files contain **synthetic test data only**.

Captured on 2026-09-08 before replacing the production `VaultCryptoServiceImpl` String DTO codec. The temporary capture test called that existing service with the production `AesEncryptionManager`, then saved each encrypted output and its decrypted JSON. The capture test was removed after capture to prevent regeneration by the new codec.

- Key: 32 bytes with values `00 01 ... 1f`; never a real master seed or user key.
- Envelope: original 12-byte random IV followed by AES-GCM ciphertext and the 128-bit tag.
- `.aes.base64`: standard Base64 of the frozen encrypted envelope.
- `.json`: exact original serializer plaintext bytes, with no version/type/fields wrapper.
- Password includes null notes; card includes empty notes; note includes newline, NUL, and Unicode. Custom fields include quote, backslash, newline, and supplementary Unicode.

`VaultPayloadCodecTest` reads the old ciphertext using the real AES implementation, compares new encoding to frozen old JSON bytes and an independent JSON parser, and verifies edit/reopen for all three types. Do not regenerate these fixtures to make a changed writer pass.
