# FIDO navigation static source no-use review

**Date:** 2026-11-17  
**Scope:** `Fido2Destinations.kt` and `TestDirection.kt`.

`Fido2Destinations` declares fixed route-name constants only. It contains no data argument, credential or paired-host value, conversion, logger, serializer, or persistence owner. The dynamic paired-host address is added by `Fido2RegistrationNavGraph` and is separately tracked by `U-FIDO-PAIRED-NAVIGATION-ADDRESS-001`.

`TestDirection.kt` contains only a package declaration and comment; it declares no executable source or data owner.

Both files now have file-specific `Assessed-no-relevant-usage` rationales in `source-inventory.json`. This is a source disposition only and does not close the surrounding FIDO navigation or UI boundary review.
