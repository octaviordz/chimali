# Validation Guide: Enforce Coverage Targets

**Date**: 2026-10-24  
**Status**: Planned implementation validation. New commands below are interfaces to implement, not tools already verified by this planning run. No application tests or coverage baselines were executed while writing these artifacts.

Use [plan.md](plan.md) for sequencing, [data-model.md](data-model.md) for result meanings and [the workflow contract](contracts/coverage-workflow.md) for arguments, exit codes and acceptance behavior.

## 1. Prerequisites and scope

**Current owner waiver (2026-10-26):** T046–T049 and their duplicate operational scope in T089 are waived because acceptable disposable Windows/Android resources are unavailable and the owner rejects malicious-PR exposure of the persistent host/device. The registered runner is disabled; do not start it or execute the hosted provisioning/demonstration steps below without explicit owner authorization. Continue trusted local measurement and tests. Hosted publication/integration outcomes remain unverified; the hosted portion of final acceptance is covered by the exception, while local checks, floors, comparison, retained evidence, and documentation remain required. See `tasks.md` and `implementation-evidence.md` §11.28.

1. Implement the policy, collector, verifier and fixture harness before executing this guide. Synchronize the authorized constitution/BRD/TRD/rules amendment and current instructions. Confirm the hard floors are Tier 1 instructions/branches 60%/60%, repository instructions 50%, and existing affected Tier 2 instructions 20%. The branch improvement goal is approximately 65%.
2. Use a clean identified candidate commit and a separate reviewed trusted harness revision. Preserve outstanding work before preparing the measured commit; never report HEAD as tested while uncommitted production/test changes are present.
3. Provide PowerShell 7, Git, the repository-compatible JDK/SDK toolchain, Gradle wrapper dependencies, Android platform tools and a connected authorized device that can execute every required procedure. Select its serial and verified capability profile. Historical Android 10 exclusions are not a default.
4. Resolve the real accepted integration target explicitly. The local cached `origin/HEAD` currently names `lab/or/jpc`; verify live configuration before using it. Do not substitute an assumed `main` branch.
5. Provision an evidence directory outside measured checkouts. Complete hosted acceptance additionally requires authenticated repository access, a clean device-capable worker, the protected evidence archive branch and configured required-check protection. Those external controls were not verifiable in the planning environment.

### Recreate the local Windows host environment

The host profile is checked into `tools/coverage/host-profile.json`; `tools/coverage/setup-host.ps1` reads the Gradle daemon JVM and wrapper pins from the repository, checks the Android SDK package revisions, and sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `PATH` for the current PowerShell process only. It can ask the Gradle wrapper to provision its pinned daemon JDK when the launcher has Java 17 or newer. It does not modify machine-wide or persistent user environment settings.

On a new Windows checkout, install PowerShell 7, Git, a Java 17+ launcher runtime, and Android SDK Command-line Tools 22.0 under the selected SDK root. Then run:

```powershell
. .\tools\coverage\setup-host.ps1 -InstallAndroidPackages -DeviceSerial '<authorized-device-serial>'
```

Dot-source the script in each PowerShell session so later Gradle/capture commands inherit its process-scoped environment. The install switch asks `sdkmanager` to install the pinned Android platform and build-tools plus the current platform-tools package; accept SDK licenses first if required. The script validates Command-line Tools 22.0, Android platform `android-37.0` revision 2, build-tools 37.0.0, and platform-tools 37.0.1. Since Android publishes `platform-tools` as an unversioned SDK Manager package, use the official revisioned archive when recreating this profile after that package advances. The current SDK root is discovered from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, ignored `local.properties`, or `%LOCALAPPDATA%\Android\Sdk`, in that order. Pass `-AndroidSdkRoot` to select another root. It validates the connected device is authorized and meets the profile's minimum API level; it does not claim that API level alone proves every hardware capability.

The command-line tools package itself is a bootstrap prerequisite and must be installed at revision 22.0. Keep its official SDK package source available when recreating a machine. A missing or different SDK package causes a hard failure rather than silently using another revision.

Run these read-only checks from the repository root:

```powershell
Set-Location 'D:/octav/source/repos/Chimali'
git status --porcelain
git rev-parse HEAD
git --version
pwsh -NoProfile -Command '$PSVersionTable.PSVersion'
adb devices -l
```

Expected: no uncommitted measured input changes, an identified source SHA, PowerShell 7, and the explicitly selected device online and authorized. Actual capability preflight is performed by capture; a listed device alone does not prove required cryptographic/platform procedures are supported.

## 2. Validate the tooling contract before measuring product coverage

After the new fixture harness is implemented:

```powershell
pwsh -NoProfile -File ./tools/coverage/test-policy.ps1
```

Expected: exit 0 with named fixture outcomes, including deliberate rejection cases. Fixtures must cover:

| Input or condition | Expected decision |
| --- | --- |
| Exactly 60% Tier 1 / 50% repository | Applicable floor passes. |
| 59.996% displayed as 60.00%, or equivalent just-below aggregate value | Floor fails using raw counts. |
| Candidate decreases from 70% to 69%, both above 60% | Changed-module regression fails. |
| Very large covered/missed counts | Exact arithmetic produces the correct decision without overflow. |
| Zero measured branches and executable instructions | Branch-only not applicable; instruction/tests still required. |
| Real JaCoCo branchless/empty XML with omitted zero-total counters and bound analysis proof | Explicit canonical zeros accepted; apply evidenced N/A only to the appropriate scope/metric. |
| Omitted XML counter without proof, with nonzero proof, or with a different inventory/report | Completeness fails; omission is not zero. |
| Missing counter or missing source/class accounting | Completeness fails; not an empty-module exemption. |
| Empty authenticator, then a fixture with executable output | Explicit empty disposition, then mandatory applicable coverage/test execution. |
| Failed, skipped, missing or zero-execution required suite | Acceptance fails even when a percentage exceeds its floor. |
| Old XML or execution data, wrong run/source identity, mutated input tree | Current-run acceptance fails. |
| Same production class counted twice or incompatible class/probe variant | Accounting/completeness fails. |
| Wrong target SHA, incompatible policy or candidate-supplied forged baseline | Comparison remains unverified/failed. |
| Target advances during assessment | Reverification required; earlier decision cannot authorize the new target. |
| Changed shared test/build configuration | All affected Tier 1 modules included in regression assessment. |
| Module rename/split/merge or executable-to-empty transition | Reviewed comparable scope mapping required. |
| Higher-risk case above 60%, lower-risk case below 60% | Higher-risk case selected first. |
| Equal risk with different module deficits | Larger measured deficit selected first. |
| A high-risk test fails while lower-risk work is queued | Repair and focused success must precede `Verified`; fresh measurement/reranking must precede lower-risk progression. |
| Every individual floor passes but repository instructions are below 50% | Extend justified remaining-module candidates; integration remains blocked until a fresh aggregate passes. |
| Candidate emits a same-named successful Actions coverage check | It cannot satisfy the required dedicated coverage App ID. |
| Missing/altered archive bundle or publication failure | Integration cannot pass. |

These fixtures validate decision logic only. They do not establish product coverage or hosted protection.

## 3. Capture the initial complete baseline

Set the following values for the real environment. Replace the two angle-bracket values before running the examples. The example external evidence directory is a local location, not a claim that it already exists or is published.

```powershell
$coverageRepoRoot = (Get-Location).Path
$coverageSourceSha = (git rev-parse HEAD).Trim()
$coverageHarnessRoot = $coverageRepoRoot
$coverageDeviceSerial = '<authorized-device-serial>'
$coverageTargetRef = '<actual-protected-integration-target-ref>'
$coverageEvidenceRoot = 'D:/octav/coverage-evidence/058'

pwsh -NoProfile -File ./tools/coverage/capture.ps1 `
  -RepositoryRoot $coverageRepoRoot `
  -SourceRevision $coverageSourceSha `
  -Purpose Initial `
  -HarnessRoot $coverageHarnessRoot `
  -DeviceSerial $coverageDeviceSerial `
  -OutputRoot $coverageEvidenceRoot
```

Expected: a new immutable run bundle with all five Tier 1 entries, full repository aggregate, required host/device results, canonical `counters.json`, raw XML, actual variants/exclusions and all ten TRD evidence fields. Canonical counts come from analysis of the identical retained compiled scope before XML omission; verify analyzer/inventory/report bindings and present-counter agreement. Omitted zero XML counters require explicit proved-zero canonical counts, while unexplained absence remains incomplete. Executable modules have successful actual test execution; empty modules have inventory evidence. The app's connected contribution must match its selected `shrunkDebug` production variant. Native iOS and any unsupported procedures are explicitly limited rather than implicitly covered.

Exit 1 can be the expected **measured floor failure** while initial deficits exist. Preserve it. A failed suite or incomplete device/report capture is not a valid complete test-success baseline; repair the prerequisite and capture a fresh run. Do not accept historical reports from `build/reports/hdk-coverage/` as a substitute for this run's provenance.

## 4. Select and improve tests

Create `test-selection.md` from fresh reports and requirement/assertion review. Use the record fields and ranks in the data model. Before implementation, record each selected case's behavior, stable BRD/TRD IDs, actual gap/weak assertion, impact/likelihood rationale, input/state, expected outcome, forbidden effects and existing-test extension/new-test decision.

Rank risk across every Tier 1 module, even those already passing. Use exact module deficit only among equal-risk cases. Investigate the ownership, consent, decryption, event-integrity and authorization leads in research, but retain the evidence-supported work order rather than starting in domain automatically. A candidate with no expected percentage gain may still be first because its missing assertion protects a critical invariant.

Place neutral behavior in configured common tests. Verify platform/crypto/database boundaries in their actual host/device test sources. Check whether a legacy test is discovered before copying or relocating it. Use independent expected values and bounded deterministic race coordination.

Examples of existing task entry points for a selected batch:

```powershell
./gradlew.bat :core:domain:testAndroidHostTest --no-configuration-cache
./gradlew.bat :core:security:testAndroidHostTest --no-configuration-cache
./gradlew.bat :core:data:testDebugUnitTest --no-configuration-cache
./gradlew.bat :feature:fido2:testAndroidHostTest --no-configuration-cache
```

For every batch, select the next highest-ranked work, create/extend its tests, run focused suites, immediately repair any exposed bounded defect and rerun until successful before marking behavior `Verified`. Then prepare a clean measurement checkpoint under the local-CI commit policy, capture fresh coverage and rerank before lower-risk work. Unresolved failure stops the batch; T059/T060 audit receipts later and do not postpone repair or measurement. A focused success alone does not refresh full coverage.

After individual module floors pass, inspect repository instructions independently. If below 50%, extend `test-selection.md` with requirement-backed `AggregateFloor` behaviors from remaining production modules, including Tier 2, and record exact test paths/expected outcomes. Keep higher-risk outstanding Tier 1 work first, apply the existing 20% gate to newly changed Tier 2 owners, preserve Tier 1 floors/nonregression and repeat the same batch loop until a complete aggregate reaches 50%. Merely expanding the ledger is not closure. Report progress toward 65% Tier 1 branches separately from the 60% hard floors.

## 5. Run final complete integration verification

After remediation is committed and the measured candidate is clean:

```powershell
pwsh -NoProfile -File ./tools/integration-ci.ps1 `
  -RepositoryRoot $coverageRepoRoot `
  -CandidateRef HEAD `
  -TargetRef $coverageTargetRef `
  -HarnessRoot $coverageHarnessRoot `
  -DeviceSerial $coverageDeviceSerial `
  -EvidenceRoot $coverageEvidenceRoot
```

Expected: full non-mutating static/compile checks, fresh required host/device execution, current accepted-target resolution, a compatible archived or freshly measured target baseline, exact module/aggregate floors and changed-Tier-1 regression decisions. A comparison target may be complete with passing tests but below new floors; that is a baseline observation, not new integration approval. The candidate independently must pass all applicable floors and comparison rules.

A successful local verification remains unverified for remote integration until the trusted hosted job completes publication and the protected required check. This prevents a local exit code from being mistaken for a proven merge-control configuration.

Use trusted published target evidence or let orchestration measure the target in isolation with the same reviewed external harness. Do not merge target execution data into the candidate. Source/test counts may differ; comparison requires equivalent measurement rules/workload, not identical test names or source bytes.

`tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` and the historical direct Gradle coverage task remain useful compatibility diagnostics. Unless they also perform current-target comparison, trusted publication and the complete integration contract, their success must not be described as complete integration acceptance.

## 6. Prove hosted integration blocking and evidence retention

Implement the unprivileged `.github/workflows/coverage.yml` capture workflow and bootstrap `.github/workflows/coverage-report.yml` on the actual protected default branch. The reporter runs through `workflow_run`, loads only externally pinned protected reporter/harness code and invokes `tools/coverage/report.ps1`; it never checks out or executes candidate/bundle files. Install the dedicated coverage GitHub App and record its real App/installation IDs. Store its key only in the `coverage-reporting` environment restricted to the exact default-branch ref, excluding PR/merge refs, and verify the device worker has no App/publication credentials.

Configure the actual integration target's required `coverage-integration` context specifically from this App ID, with strict current-target handling and no accepted bypass. Verify reporter workflow commit, pin, capture run/attempt, head/base/merge/tree identities and publication receipts. Protected target-push handling must invalidate old-base decisions even when the head does not change. Register the disposable device worker and protected archive/publisher. These are implementation/provisioning actions, not capabilities established merely by writing workflow files; absent App/environment/source-binding support remains blocking with no generic-token fallback.

Using controlled proposed changes, demonstrate that integration is blocked when:

- ordinary local checks pass but the required coverage check is missing, pending, cancelled, skipped or failed;
- a required device/suite/report is missing or unsupported;
- a Tier 1 metric or repository aggregate is below its floor;
- a changed module regresses while staying above 60%;
- the candidate or accepted target changes after verification;
- supporting published evidence is missing, altered or inaccessible.
- a candidate replaces capture/reporting workflow content with a same-named success-only job: its GitHub Actions result cannot satisfy the required App source, and its PR/merge ref cannot obtain the protected App key;
- a completed event is from another repository or an obsolete capture attempt, or its accepted base changed without a head change.

Then demonstrate a complete passing proposed merge with the same protected check and accessible artifacts. Record the actual branch/ruleset settings, check identity/source, tested head/base/candidate/tree identities and named blocked/passing attempts. If remote configuration or device access is unavailable, keep this acceptance item unmet; local fixture success cannot close it.

After actual integration, verify the integrated tree equals the tested tree before promoting its accepted baseline index. Any content-changing rebase/squash/conflict resolution requires a fresh run. Confirm required reports remain readable from the protected archive after local build directories are replaced or transfer artifacts expire. Do not delete referenced baseline or active-review artifacts.

## 7. Final feature evidence

Record actual results in `implementation-evidence.md` using the exact ten TRD keys. Link the initial and final immutable run bundles, selection records, raw counter changes, complete test counts, device/host/tool/variant identities, accepted-target comparison, exclusions, archive hashes and hosted blocked/passing integration proof. Distinguish runtime `Passed`/`Failed`/`Deferred`/`Unverified` from documentary governance states.

Preserve any 65% goal shortfall, stronger business-logic coverage objectives, native-platform exclusions, instrumentation limitations and prior feature-055 performance/device deferrals. Do not rewrite October 18 observations to appear current.

The planning output has not satisfied any of these execution checks. Feature completion requires measured SC-001 through SC-008 outcomes and retrievable supporting artifacts.
