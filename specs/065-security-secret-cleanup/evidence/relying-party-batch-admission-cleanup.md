# Relying-party batch admission cleanup

Date: 2026-11-18  
Finding: SEC065-FIDO-OWNER-001  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/RelyingPartyRepositoryImpl.kt`

## Ownership correction

`getAllRelyingParties` mapped each source row into a mutable `RelyingParty` owner and then appended it to the batch cleanup list. If list admission failed, the current model was not in the list and the terminal cleanup could not reach it. The row owner was still erased, but the newly constructed model’s independent text owners were stranded.

The repository now calls `admitOwnedRelyingParty` for each model. The helper transfers the model only after the batch accepts it and erases the model on failed admission, including an exception or a rejected `add` result. Existing batch cleanup continues to own previously admitted models.

## Verification and limits

`RelyingPartyRepositoryFlowOwnershipTest` passes seven cases, including a rejected batch admission that verifies the unadmitted model’s mutable RP ID and name owners are erased. The full FIDO host suite passes 1,348 tests across 175 reports with zero failures, errors or skips; FIDO KtLint passes.

The host seam does not model allocator exhaustion or prove SQLDelight/native copies, provider behavior or heap-root absence.
