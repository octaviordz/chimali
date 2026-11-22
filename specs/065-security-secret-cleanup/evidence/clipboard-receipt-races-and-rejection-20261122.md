# Clipboard receipt race and rejection follow-up

- Date: 2026-11-22.
- Revision: dirty worktree based on frozen candidate `2627644114f95f7f0c33e7448abc5e0f3961cc26`; the edited sources are not part of that candidate. This evidence is exploratory implementation evidence, not candidate proof or release acceptance.
- Scope: Android and iOS receipt admission/status insertion is atomic with acknowledgement, abandonment, and custody transfer. Android confirmed supersession releases the prior token, job, and publication metadata. Android scheduled clear rejection has a distinct sanitized `ClearRejected` outcome and the vault caller reports cleanup unconfirmed, then acknowledges the receipt.
- Verification command: `./gradlew :core:common:testAndroidHostTest :core:common:ktlintCheck :feature:vault:testDebugUnitTest :feature:vault:ktlintCheck`.
- Result: BUILD SUCCESSFUL. Core Common Android host clipboard suite: 30 tests, zero failures/errors/skips. Vault unit suite and both module KtLint checks passed. iOS sources passed KtLint; native iOS compilation/runtime was not executed on this Windows host.
- Focused assertions: publication interleaved with acknowledgement preserves the new pending receipt and recovers capacity; existing repeated-copy/deadline and bounded receipt tests pass; a rejected scheduled Android clear is terminal after one attempt and remains distinct from unreadable metadata; the vault caller displays “Clipboard cleanup couldn't be confirmed.” and acknowledges it.
- Limits: no Android instrumentation or iOS runtime test was run for these source changes. No platform call result is represented as observed clipboard removal. A fresh frozen candidate and review of changed product sources remain required.
- Artifacts:
  - `core/common/build/test-results/testAndroidHostTest/TEST-com.chimali.core.clipboard.AndroidClipboardManagerServiceTest.xml` SHA-256 `DC5823FE3C95FDCF074EB3726E9CE25BD9BB5CA86A4F87DE09515617FB3D1BBA`.
  - `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.VaultViewModelTest.xml` SHA-256 `7EBCAE9FF893FBF8EB5A618B0F3EE87F173B2A9A57838A2A2E4CAD2FAD602323`.
  - `core/common/src/androidMain/kotlin/com/chimali/core/clipboard/AndroidClipboardManagerService.kt` SHA-256 `D8661C2DE346CEAE356AFD357F77638E7204A13F896F2FE72FC93DF247F507B3`.
  - `core/common/src/iosMain/kotlin/com/chimali/core/clipboard/IosClipboardManagerService.kt` SHA-256 `65913831D97AE074EC0FD8D1EBDE7144AEEDBFF52A743402D768A1BB8C45DB33`.
  - `core/common/src/commonMain/kotlin/com/chimali/core/clipboard/ClipboardManagerService.kt` SHA-256 `6580C2386824098ADABCC2CA9ED5B454A380E719F847F538480CE5F7494B44B1`.
  - `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` SHA-256 `E5526A34C8299D3BA7238116781013F98B596A24C3105F0BDF51ADFFE41286DF`.
