# Vault secret rendering immutable boundary

Date: 2026-11-15  
Scope: `LegibleSecretText` and Compose secret text state.

When a secret is masked, `secretDisplayText` creates only bullet characters based on the input length. When revealed, `buildLegibilityAnnotatedString` appends each secret character to a Compose `AnnotatedString`, optionally adding grouping spaces and semantic spans; `BasicText` then owns/renders that immutable text. The composable borrows the source `CharArray` and does not clear it; callers remain responsible for terminal erasure.

`SecretRenderingAdapterTest` covers masked and revealed formatting using synthetic arrays. `ComposeSecretRetentionTest` demonstrates that `TextFieldState.clearText()` leaves a previously retained immutable `String` snapshot readable; its own comment correctly marks this as characterization rather than security acceptance.

Clearing the app-owned source array does not erase an `AnnotatedString`, prior Compose snapshot, text layout cache, or renderer/provider copy. The secret is only materialized in visible plaintext when `isRevealed` is true, and output lifetime depends on Compose/framework release. Runtime renderer roots, accessibility/TalkBack copies, screenshots and heap evidence remain unresolved under T006/T015/T027. No claim of UI-wide erasure is made.
