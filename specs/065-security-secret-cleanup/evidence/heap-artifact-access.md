# Controlled heap-artifact access preflight

## Scope

This preflight prepares a private local destination for future synthetic Android HPROF captures under T015. It does not record a heap dump and does not establish full T045 completion.

## Destination and ACL

- Absolute destination: `C:\Users\octav\AppData\Local\Chimali\SEC065-heap`
- The directory is outside the repository and outside the normal app build output tree.
- Verified owner: `LOQ15IRX10-3\octav`.
- Inheritance is disabled. The only access rules are FullControl for that user and `NT AUTHORITY\SYSTEM`.
- No HPROF, expected-marker dictionary, or other capture file existed at preflight time.
- Captures must come only from the unique `com.chimali.heapaudit` process on the API 35 emulator. Do not capture production or personal-data process IDs.

## Synthetic state and handling

Each run will use a unique run ID and freshly generated synthetic inputs. The external expected-marker dictionary and raw HPROF files must remain under the restricted destination; publish only hashes, counts, object types, root classifications, and limitations in the repository. Do not copy raw HPROF or marker values into Git, Gradle reports, Logcat, or shared cloud folders.

Keep raw captures and the expected-marker dictionary accessible to the authorized independent reviewer while review depends on them. After review and any required follow-up are complete, delete raw captures and dictionaries, retain a disposal record and sanitized findings, then recheck the directory ACL. Because no reviewer or captures are currently registered, there is nothing to dispose yet and T045 remains open.

## Verification result

On 2026-11-17, PowerShell ACL inspection confirmed the protected directory, owner, disabled inheritance, and the two expected FullControl entries. The API 35 heapAudit target and test packages were removed after the lifecycle test. No raw heap capture has been made.

## 2026-11-19 re-verification and raw-capture loss finding

On 2026-11-19 the destination `C:\Users\octav\AppData\Local\Chimali\SEC065-heap` was found absent, including its parent `Chimali` directory. The five retained heap-capture records (`heap-capture-mnemonic-api35-20261117.md`, `heap-capture-passkey-api35-20261117.md`, `heap-capture-vault-note-api35-20261117.md`, `heap-capture-vault-password-card-api35-20261117.md`, `heap-capture-wallet-derivation-api35-20261117.md`) cite raw HPROF files and the synthetic expected-marker dictionary under that destination by SHA-256; those raw artifacts are no longer accessible. This is an evidence-access loss, not a product-behavior result: the sanitized repository records and their recorded hashes remain, but the raw captures they reference cannot be re-inspected by a reviewer.

The controlled destination was recreated on 2026-11-19 with the same contract: owner `LOQ15IRX10-3\octav`, inheritance disabled (`AreAccessRulesProtected=True`), and only that user plus `NT AUTHORITY\SYSTEM` holding FullControl. The directory is empty; no capture or marker dictionary exists. Prior raw captures were not recoverable from this session.

T045 remains open: controlled accessibility for the *existing* cited raw artifacts cannot be re-established because the files are gone; future captures must regenerate raw artifacts under the recreated destination, and the loss must be disclosed in the frozen-candidate evidence reconciliation (T036/T037/T044).

## 2026-11-20 regeneration — wallet recovery/derivation capture

The controlled destination was re-verified on 2026-11-20 (owner `LOQ15IRX10-3\octav`, `AreAccessRulesProtected=True`, only that account and `NT AUTHORITY\SYSTEM` with FullControl) and now contains the regenerated run `sec065heap20261120b` artifacts: five raw HPROFs, the sanitized `.analysis.txt` outputs, `sec065heap20261120b_capture-summary.json`, `sec065heap20261120b_instrumentation.txt`, the restricted `sec065heap20261120b_expected-markers.txt` dictionary, and the rebuilt Shark 2.14 toolchain under `tools\` (jars plus `HeapMarkerInspector.java`). Orchestrator `tools/security/run-heap-capture.ps1` is versioned in the repository. See [heap-capture-wallet-derivation-api35-20261120.md](heap-capture-wallet-derivation-api35-20261120.md) for hashes and sanitized results.

These artifacts are reviewer-accessible while cited. On 2026-11-20 all four remaining owner groups were regenerated the same way: run `sec065heap20261120b` (wallet recovery/derivation, five checkpoints), `sec065heap20261120c` (vault note), `sec065heap20261120d` (vault password/card) and `sec065heap20261120e` (passkey identity). Records: `heap-capture-wallet-derivation-api35-20261120.md`, `heap-capture-vault-note-api35-20261120.md`, `heap-capture-vault-password-card-api35-20261120.md`, `heap-capture-passkey-api35-20261120.md`. The mnemonic-only scoped record is superseded in coverage by run `b`'s five-checkpoint superset of the same lifecycle. Raw-artifact coverage for all previously cited owner groups now exists again under the controlled destination; the 2026-11-17 artifacts themselves remain unrecoverable and their records stay historical. One disclosed nuance: run `e` shows an encoded credential-ID String still JavaFrame-reachable at terminal (frame-local retention candidate) where the 2026-11-17 run reported none — flagged for reviewer adjudication. T045 remains open pending retention/disposal scheduling and candidate-bound rerun.
