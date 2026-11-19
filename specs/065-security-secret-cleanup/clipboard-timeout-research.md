# Clipboard timeout: industry practice investigation

**Investigated**: 2026-11-13. **Status**: Research complete; the owner selected 30 seconds after reviewing these findings. The approved specification now uses 30-second best-effort scheduling with ownership checks and skip-on-uncertainty. Published comparison values below retain their original meaning.

## Findings from primary sources

| Product or guidance | Published interval | Scope and evidence |
| --- | --- | --- |
| KeePassXC | 10 seconds by default | Desktop; the pinned [2.7.11 configuration](https://github.com/keepassxreboot/keepassxc/blob/2.7.11/src/core/Config.cpp) enables clipboard clearing and sets its timeout to 10. This is a version-specific source default, not an Android result. |
| Enpass | 30 seconds by default | Its [current help](https://help.enpass.io/personal/latest/all/clearing-clipboard-contents) documents that default and separate desktop, iOS and Android controls. Its [business policy guidance](https://help.enpass.io/business/latest/google-workspace/app-security-policies) recommends retaining 30 seconds or another short interval. |
| Proton Pass | 60 seconds | The [Android manual](https://proton.me/support/use-pass-android) documents clearing after 60 seconds and a setting to adjust it. This is the closest direct mobile comparison to Chimali. |
| 1Password | 90 seconds | Its [current support article](https://support.1password.com/copy-passwords/) documents automatic clearing, including Android, and an option to turn it off. |
| Bitwarden | Configurable; documented range 10 seconds to 5 minutes | The [vendor article](https://bitwarden.com/blog/vault-security-bitwarden-password-manager/) lists this range. The current [app-settings help](https://bitwarden.com/help/app-settings/) exposes the setting, but this research does not claim a universal current default across its clients. |

These are product documentation/source observations, not installed-app measurements or a representative statistical survey. Defaults can differ by platform/version or change over time. For example, older Proton roadmap material described two minutes; the current Android manual used above says 60 seconds. Historical or community posts are not substituted for current primary documentation.

## Security guidance versus product defaults

[OWASP MASWE-0030](https://mas.owasp.org/MASWE/MASVS-PLATFORM/MASWE-0030/) advises avoiding secret clipboard use where possible, copying only through a user-chosen action, marking sensitive content, and using a short expiration. It does not prescribe a numeric timeout on that page.

[Android's secure clipboard guidance](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling) includes a clearing example scheduled after **5 seconds**. That is an implementation example, not a universal password-manager standard or proof of reliable timing across process termination and access restrictions. Its discussion also highlights the additional background-reading exposure on Android versions before API 29; Chimali currently supports API 28.

Conclusion: there is no single timeout established by these sources. Observed named-product defaults span 10–90 seconds in this sample; 60 seconds is used by a major Android password manager and is not an outlier. Shorter intervals are also well established. Product popularity or default choice is not evidence of an optimal or mandated security threshold.

## Recommendation for Chimali

**Propose 30 seconds**, keeping the previously approved best-effort and ownership-check rules. This halves the intended waiting interval relative to the current 60-second policy and matches Enpass's explicit default/recommendation. It offers more room for switching apps and locating the destination field than a 10-second interval. That usability judgment is an engineering recommendation, not a measured result for Chimali or an industry mandate.

**10 seconds** is a defensible stricter option, supported by the desktop KeePassXC comparison, but requires particular attention to mobile app switching, slower interactions and accessibility. Neither 10 nor 30 seconds prevents immediate reading by an eligible app or erases clipboard history/provider copies. Shortening the delay also does not solve Android process-lifecycle, observation or check-to-clear race limits.

Do not introduce a new configurable-timeout settings flow merely to resolve this question; it would add product scope beyond selecting the feature's current delay. Any such setting needs an explicit separate decision.

## Follow-through once selected

The owner answer is recorded in the specification and active NFR-SEC-050/TRD policy. Historical audit dates and original 60-second test evidence remain intact; old timing tests do not verify the new interval. Implementation now uses a fixed 30-second Android schedule and truthful best-effort copy feedback. Host timer, ownership and failure checks are recorded in the evidence index; user-facing Android app-switching, accessibility and lifecycle evidence remains open.

## Owner decision

The owner selected 30 seconds and requested explicit inclusion of the OWASP/Android sensitive-content marking recommendation. The specification and current authorities are synchronized. The Android adapter sets the equivalent sensitivity key before publication. Physical preview behavior and interrupted-process cleanup remain unverified.
