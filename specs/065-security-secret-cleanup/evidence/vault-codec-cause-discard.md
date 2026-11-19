# Vault metadata codec cause-discard evidence

**Finding:** SEC065-ERR-015  
**Date:** 2026-11-16  
**Scope:** `VaultMutableJsonCodec` malformed event decoding on the Windows JVM host.

The decoder now converts lower-level timestamp/parser failures to a fixed `SerializationException` without retaining the original throwable. The regression supplies a synthetic invalid timestamp marker and asserts that the returned error has no cause and does not contain the marker. The test also clears its input byte array in `finally`.

`VaultMutableJsonCodecTest`: 4 tests passed, 0 failures, errors, or skips. The repository local CI pipeline passed KtLint, Detekt, release lint, compile-all, unit tests, and configured KMP Android host tests in the same dirty exploratory worktree. A subsequent focused codec run passed the four codec tests and `:core:data:ktlintCheck`.

This is source/host evidence only. It does not establish absence of details at all configured diagnostic destinations, provider/native copies, or full persisted-data owner closure. No frozen candidate or hosted integration CI result is claimed.
