# Evidence validator follow-up summary

- Date: 2026-11-22.
- Candidate-mode invocation: `pwsh -NoProfile -File tools/security/validate-feature-065-evidence.ps1 -CandidateRevision 2627644114f95f7f0c33e7448abc5e0f3961cc26 -ReportRelativePath specs/065-security-secret-cleanup/evidence/candidate-validation-followup-20261122.md`.
- Candidate-mode result: 626 records, 1,715 artifact hashes, zero candidate errors, 8/8 synthetic invalid records rejected. This validates the earlier frozen candidate and evidence index only; it does not include later clipboard implementation edits.
- Historical-mode invocation: `pwsh -NoProfile -File tools/security/validate-feature-065-evidence.ps1 -ReportRelativePath specs/065-security-secret-cleanup/evidence/historical-validation-followup-20261122.md`.
- Historical-mode result: 626 records, 1,715 artifact hashes, one error, 8/8 synthetic invalid records rejected. The only error is the unavailable historical FIDO XML with original expected SHA-256 `a4bbad580da6f86e04590e02e501796a931f32f1834a438eda09b4f3bbbab161`; its row remains Unverified and the digest is retained unchanged.
- Reports: `candidate-validation-followup-20261122.md` and `historical-validation-followup-20261122.md` in this directory. They are generated validation outputs; this summary retains the run parameters and results without creating a self-referential report digest.
- Status: candidate validator mode is clean for the old freeze; historical mode reports the disclosed missing artifact. Neither result establishes a new candidate or release acceptance. Run final validations again after the delivery revision is frozen and affected evidence/review is refreshed.
