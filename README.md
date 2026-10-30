# Chimali

Chimali is an Android and Kotlin Multiplatform credential vault with passkey support.

## Development and coverage workflow

- Feature branch pushes do not require a coverage device or evidence setup. Configure `coverage.targetRef` only when you want the local pre-push hook to check a specific remote branch.
- Run optional coverage assessment with the trusted `tools/integration-ci.ps1` workflow after setting up the pinned toolchain, clean trusted harness, authorized Android device and external evidence directory. The result must identify the exact candidate and accepted baseline.
- Before a pull request is accepted, provide a link to its immutable coverage report and have an authorized repository user review the report, policy, suites, floors, comparison and tested tree.
- Use merge commits for integration. If the proposed merge tree differs from the tree in the report, assess the changed feature commit before acceptance. An advance of the target alone does not require a new coverage run when the proposed tree remains the same.
- Promote a baseline only after the integrated tree matches the tested tree and the archived evidence passes digest and concurrency checks.

`main` is the GitHub default branch. GitHub rules protect `main` and `swe/coverage-evidence`, and the accepted baseline has been promoted after a passing controlled end-to-end review. The old `codex/coverage-evidence` ref was removed after verifying its commit history and contents are preserved in `swe/coverage-evidence`; old receipt identities remain historical records. GitHub does not enforce the coverage review, and local hook success is not a hosted merge check. The hosted worker remains disabled under the owner-authorized waiver.

See the [manual coverage review guide](specs/061-manual-coverage-integration/quickstart.md) and [Feature 058 coverage policy](specs/058-enforce-coverage-targets/quickstart.md).
