# Passkey management screen lifecycle owner cleanup

**Finding:** SEC065-FIDO-014 — the passkey management ViewModel retained RP/user-associated credential owners and search state after its screen stopped.

`CredentialListScreen` now signals screen start/stop to its ViewModel, collects state and UI effects only while started, and clears its local search and pending biometric prompt on stop/disposal. `CredentialManagementViewModel` invalidates in-flight search/page generations, cancels search work, empties state and pending-removal owners, erases all unique mutable credential text owners and the query owner, discards queued removal identifiers without committing deletion and ignores delayed screen intents, including a biometric callback arriving after stop. On start, it reloads credentials.

The `CredentialManagementViewModelTest` suite passes 29 cases, including `screen stop erases retained credential owners and resume reloads the list`; FIDO KtLint passes. The synthetic case also starts a pending swipe-removal, stops the screen, verifies pending state is discarded and old mutable owners are erased, sends delayed biometric-confirm and commit-delete intents and verifies they are ignored, then verifies a fresh list is loaded on resume.

This proves cleanup of mutable owners held by this ViewModel and its screen-local search/prompt state. It does not prove release of Compose/accessibility/biometric immutable strings, database/provider copies or heap roots. No physical UI or Android instrumentation result is claimed.
