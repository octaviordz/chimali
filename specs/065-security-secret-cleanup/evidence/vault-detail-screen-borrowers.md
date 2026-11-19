# Vault detail screen borrowers

Date: 2026-11-15  
Scope: password, card and secure-note detail screens.

The detail screens borrow decoded mutable payloads from `VaultViewModel` and clear their payload on Compose disposal. The six-case parameterized `VaultDetailOwnershipTest` confirms decoded password/card/note arrays are cleared after selected-item removal, ViewModel disposal, or a decrypt result arriving after dismissal.

The rendering conversions remain managed-text boundaries: `PasswordDetailScreen` makes `String` values for title/username/URI/notes/custom fields and uses `LegibleSecretText` for the password. Its explicit copy action creates an independent password `CharArray`, transfers it to the callback only after successful return, and clears it when the callback throws. `CreditCardDetailScreen` reveals card number through `LegibleSecretText`, masks all but the last four when hidden, and creates a `String` for CVV only while revealed. `SecureNoteDetailScreen` creates `String` text for note content and custom fields. All three clear source payload arrays on disposal, but that does not erase prior Compose text snapshots, layout/accessibility copies, or renderer state.

The source and host evidence establish owner cleanup and callback transfer at the mutable-array boundary. They do not establish Compose renderer release or heap-root removal; those remain Unverified under T006/T015/T027.
