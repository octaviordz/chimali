# iOS Clipboard Platform Boundary

**Date:** 2026-11-17  
**Finding:** SEC065-IOS-BOUNDARY-001  
**Source:** `core/common/src/iosMain/kotlin/com/chimali/core/clipboard/IosClipboardManagerService.kt`  
**Status:** Source reviewed; Kotlin/Native compilation and iOS runtime behavior Unverified.

`copySensitiveData` borrows the caller-owned `CharArray` and creates an immutable `String` because UIKit's `UIPasteboard.string` accepts a string value. The adapter does not retain that text in its scheduled work. It retains a generation, expected `changeCount`, receipt status, and a one-shot timer. At the scheduled deadline it checks `changeCount` metadata and submits a clear only when the count still matches. It never reads clipboard text to decide ownership.

The immutable handoff and OS-owned pasteboard entry are platform boundaries. The app erases its mutable caller buffer, but cannot erase Kotlin/Native, UIKit, system pasteboard, Handoff, or other OS-managed copies. The 30-second task is best effort and does not prove removal: app suspension/termination, pasteboard changes, or a failed UIKit operation can prevent or alter the result.

Apple's [`UIPasteboard`](https://developer.apple.com/documentation/uikit/uipasteboard) documentation describes the general pasteboard as persistent and provides APIs for setting items with options. Apple's [`UIPasteboard.OptionsKey`](https://developer.apple.com/documentation/uikit/uipasteboard/optionskey?changes=_3) documentation includes local-only and expiration options. The inspected adapter currently uses `pasteboard.string = String(text)` and does not request either option. Whether adopting those options is compatible with this Kotlin/Native target and product behavior is not evaluated here; no claim is made that the current scheduled clear controls Handoff or pasteboard history.

The source contains no logger, print, `NSLog`, or platform diagnostic sink; failures discard caught exception details and return fixed messages. That is the separate source-only result in `SEC065-CLIP-IOS-004`, not a review of all iOS logging destinations.

**Disposition:** BND-UI-013 and the `U-CLIP-IOS-001` inventory row now record the immutable UIKit transfer and OS-managed retention boundary. No runtime claim or implementation change is supported by this source review. iOS compilation, actual system behavior, Handoff behavior, expiration-option feasibility, and native heap retention remain Unverified.
