# Candidate-bound platform text boundary reconciliation

- **Candidate:** commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`.
- **Claim scope:** FR-005 documentary recording of platform input/rendering boundaries and their allowed purpose, mutable-interface constraint, reference lifetime and application release controls.
- **Procedure:** Read the frozen candidate's canonical `docs/security/platform-text-boundaries.md`; enumerated every registered BND identifier and cross-checked each `source-inventory.json` usage `boundary_ids` reference against it. Checked the canonical boundary rows' ten-column shape and nonempty cells. For boundary-linked usage rows, checked the inventory's owner, borrower, representation, purpose, persistence/logging limits, terminal events, assessor/date and status fields.
- **Observed:** 18 canonical boundaries are registered; all 18 rows have ten populated cells. All 18 distinct inventory boundary identifiers resolve to the register. 139 usage records link to at least one boundary; all 139 contain the checked owner/lifetime/purpose/release fields. Twelve usage rows explicitly cite FR-005. The inventory contains 736 file records and 640 usage records at the candidate identity.
- **Disposition:** Satisfied for the documented boundary-recording portion of FR-005. This is source/document validation only; it does not prove Compose, IME, accessibility, OS, clipboard-provider or cryptographic-provider copies are erased at runtime. The register expressly preserves those limits and does not grant key/seed material a text-boundary exemption.
- **Date:** 2026-11-22.
- **Artifacts:** `docs/security/platform-text-boundaries.md`; `specs/065-security-secret-cleanup/source-inventory.json`.
