# SensitiveText owner-wrapper transfer cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-001

## Source correction

`SensitiveText.copyOf`, `copyOwner`, and `takeOwnership` now route newly allocated character buffers through a guarded transfer. If constructing the `SensitiveText` wrapper fails, `takeOwnership` erases the incoming buffer in `finally`; on success, the model owns that same buffer and its normal `erase()` clears it. This also protects the copied character array used by `decodeSensitiveText` before it becomes the shared domain owner.

## Verification and limitations

`:core:domain:testAndroidHostTest --tests com.chimali.core.domain.model.SensitiveTextTest` passes all 14 tests, and `:core:domain:ktlintCheck` passes. The direct-transfer regression proves that erasing the successful owner clears the original handed-off array. Wrapper-allocation failure was not injected. Provider/serializer copies, immutable text and heap-root behavior remain outside this evidence.
