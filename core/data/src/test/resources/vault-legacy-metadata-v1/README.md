# Frozen Vault metadata JSON fixtures

These synthetic, independently authored fixtures are checked against the existing event/snapshot serializers before T039 changes their title representation. They cover the event discriminator, nested snapshot state, title JSON strings, Unicode/escapes, signed byte arrays, null updates and omitted default snapshot fields.

Do not regenerate these fixtures to make a replacement codec pass. Keep them as the old-format oracle and update the tests to exercise the replacement codec in both directions. No real credentials or keys are included. These are serialization fixtures, not AES ciphertext fixtures; the existing Vault payload AES fixtures remain separate and unchanged.
