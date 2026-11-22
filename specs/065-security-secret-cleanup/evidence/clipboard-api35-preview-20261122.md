# Current-worktree API 35 sensitive clipboard preview observation

**Date:** 2026-11-22  
**Device:** ASUS AI2302 (`R7AIB7002901AZ3`), Android 15 / API 35, unlocked  
**Build:** current dirty exploratory worktree HEAD `49a44aa36e0e86cabd771ffe6c83b079a1a9c4a5`; audit target SHA-256 `B19B50FCCB74671131729F3E5758AD82B856ED7744C9157445E10A345CF0D8ED`; instrumentation APK SHA-256 `814539C631A452C451A81834323D33F9A6E45EA911EBF33C82360B187CAEBD69`.

The isolated target published the synthetic marker with `MetadataConfirmed`. The observer's positive control reported `marker-match` and `sensitiveFlag=true`; the mismatch control reported `replaced`. The target returned `ClearRequestSubmitted` after the 30-second deadline, and the independent observer reported `absent`. The full sanitized procedure report is `clipboard-api35-preview-20261122.json` (SHA-256 `DBC83FDC62D6C2794D593B4D99EF9F7C3F9465E335A7DCCFC40A7A46CE77579E`).

Twelve screenshots were captured at approximately 250 ms intervals immediately after the target's ready receipt. A representative early frame (`frame_00.png`, SHA-256 `E13E4F74C34DD706D33EAE17ED22D32CF9AA65BBFA634ECFCD97BE861DCFFAF4`) shows the audit activity with a clipboard icon chip and no visible synthetic password text. The remaining frame hashes fall into two groups (four initial identical frames, one transition frame, and seven subsequent identical frames); visual inspection of representative frames found no visible synthetic password text. A later `dumpsys window` sample did not capture a `ClipboardOverlay` window. This is bounded non-exposure evidence for this run; it does not prove the overlay was masked rather than omitted, does not cover accessibility speech or clipboard history, and is not candidate-bound.

Both isolated audit packages were uninstalled and verified absent after the run. No real clipboard text was read or retained. Preview observation remains **partially verified**; exact masked-versus-omitted rendering and independent review remain open.
