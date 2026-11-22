# Frozen-candidate clipboard cohort — Xiaomi Mi A2 / API 29

- Date: 2026-11-22
- Frozen product candidate: commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`.
- Device: Xiaomi Mi A2 (`2ac6876`), Android 10/API 29, MIUI build `QKQ1.190910.002 V11.0.28.0.QDIMIXM`.
- Target/test package: isolated `com.chimali.clipboardaudit` / `com.chimali.clipboardaudit.test`; the regular `com.chimali` installation was not changed.
- Candidate APK SHA-256: target `B19B50FCCB74671131729F3E5758AD82B856ED7744C9157445E10A345CF0D8ED`; observer `814539C631A452C451A81834323D33F9A6E45EA911EBF33C82360B187CAEBD69`.
- Procedure: `tools/security/run-clipboard-observation.ps1`; random synthetic marker only; observer classified marker-match, replaced or absent without exporting clipboard contents. Every run's harness cleanup completed. The separate post-lock recovery run independently observed its new synthetic marker absent. Both isolated packages were uninstalled and verified absent afterward.

## Results

| Case | Scoped behavior | Independent observation |
|---|---|---|
| Scheduled attempt | Passed: `ClearRequestSubmitted` after the 30-second schedule | Passed: synthetic marker absent |
| Process death | Passed: target process stopped before scheduled execution | Passed: synthetic marker remained; no cleanup after process death is claimed |
| App switch | Passed: `SkippedOwnershipUnavailable` while backgrounded | Passed: synthetic marker remained |
| Clipboard replacement | Passed: cleanup skipped after ownership became unavailable | Passed: observer classified the replacement |
| Manual clear | Passed: `ManualClearSubmitted` | Passed: synthetic marker absent |
| Repeat copy | Passed: old generation superseded | Passed: new generation's scheduled cleanup observed absent |
| Copy rejected | Passed: admission rejected while earlier unconfirmed work was pending | Passed: earlier synthetic marker remained; harness then cleared only its matching marker |
| Locked/sleep | Passed: `SkippedOwnershipUnavailable` at the 30-second deadline | **Unverified:** secure keyguard remained after wake, so the observer could not establish removal. No removal claim is made. |

The eight scoped behavior outcomes are recorded separately from removal observation. API 28 was waived by the owner; this is API 29 evidence only. Preview behavior, iOS, other OEMs and physical API 33+ / API 35 cohorts are not established by this run. The prior Mi A2 preview probe used the same target and observer APK digests, but remains separately documented as bounded non-exposure evidence; masking versus omission is unknown.

## Retained sanitized reports

All reports are under `evidence/clipboard-api29-candidate-20261122/`.

| Case / run | SHA-256 |
|---|---|
| `scheduled-attempt` / `sec065candidateapi29sched20261122` | `2B06695D80CD0AB7D8E44B7E619FB2C318038D6DF2B1A34F519124362139094E` |
| `process-death` / `sec065candidateapi29death20261122` | `13E3FFECAF78C0DC3091EDD1383247D69F67E2C7D51A9FD499EE650004BB097F` |
| `app-switch` / `sec065candidateapi29switch20261122` | `2B5BFD07E33912612D14BFD082976FAE4A2E19F689C80E83E2E311FF122117DE` |
| `clipboard-replaced` / `sec065candidateapi29replace20261122` | `EA6276DBF919796FC0E004C02BB510144951C75F6F69B3E3A24F9FF3C7BE9257` |
| `manual-clear` / `sec065candidateapi29manual20261122` | `03E356A4C97EF9B84C5132E282FEF9A1B3783927BA84CF844FEA322DD50B3055` |
| `repeat-copy` / `sec065candidateapi29repeat20261122` | `D84B714E55FB250424AB63D35E2FF9FB03AFFF95EEBDB4B25DA4C1C377A6272C` |
| `copy-rejected` / `sec065candidateapi29reject20261122` | `185EB9F11C4334CD8161B1DA32BE9F909F17393081F62469C89F594C2EEAD3B9` |
| `locked-sleep` / `sec065candidateapi29sleep20261122` | `56F49155FCF0E495345C4A6F426ED808CA8453B733C7F1C6BDBC4FEEF4C94387` |
| Synthetic cleanup recovery / `sec065candidateapi29recovery20261122` | `EF98EE6C4E873F40E3352688B20409D37A5514F78D3D482614A97BC9DAEFD574` |
