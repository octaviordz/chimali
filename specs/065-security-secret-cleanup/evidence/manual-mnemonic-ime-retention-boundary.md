# Manual recovery mnemonic IME retention boundary

Date: 2026-11-15  
Scope: Android manual BIP39 entry in `DevelopmentToolsContent`.

`SensitiveMnemonicInputPolicy` applies `IME_FLAG_NO_PERSONALIZED_LEARNING`, `IME_FLAG_NO_EXTRACT_UI`, and `IME_FLAG_NO_FULLSCREEN` to the input connection. For text-class inputs it removes auto-correct/auto-complete and sets `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. It clears initial surrounding text and excludes the view subtree from autofill. `DisposableEffect` restores the root view's previous autofill setting when the policy leaves composition.

The existing `manualRecoveryDisablesImeLearningAutofillAndExtractedText` Android device test checks these flags, suggestion settings, empty before/after text, absence of custom IME action labels, and the autofill exclusion. This test source is mapped as relevant, but it was not executed as part of the current host suite.

The surrounding `ManualMnemonicEntryForm` owns mutable word buffers, transfers an encoded `CharArray` on submit, erases the word buffers after submit, and erases them on disposal. Rendering and input still cross Compose/Android `String` and platform IME boundaries. These flags reduce platform retention and disclosure, but cannot prove keyboard-provider compliance or erase platform/Compose copies. Device observation, third-party IME behavior and heap-root analysis remain open under T006/T015/T027.
