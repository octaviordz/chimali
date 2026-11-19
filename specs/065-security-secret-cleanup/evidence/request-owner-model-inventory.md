# FIDO request-owner model inventory

Date: 2026-11-15
Finding: SEC065-CORE-029

Mapped the previously Open GetAssertion option model and test, PRF input model/test, RP entity owner, authenticator selection criteria, and the existing domain-model test source. `GetAssertionOptions` owns mutable RP text but borrows the challenge hash, allow-list and extensions map; its copy operation creates a separate RP owner while sharing the other references. The CTAP handler clears recursively decoded CBOR byte/text values and the converted challenge hash in its terminal block. PRF derivation borrows PRF salt values from this decoded map, and `PrfExtensionInput` does not claim ownership or create copies. This links its salt lifetime to the request-map cleanup owner.

`PublicKeyCredentialRpEntity` owns mutable RP ID/name text and has explicit erase/copy APIs. `AuthenticatorSelectionCriteria` retains a borrowed allow-credential list whose decoded arrays are owned by the request map. Existing host tests cover model validation/copy/redaction in part; this inventory pass did not execute them. Test arrays are synthetic.

No owner completeness is claimed for all request consumers, public immutable boundary strings, collector lifetimes, provider copies, or heap roots. See T012/T015.
