# Controlled heap-artifact access preflight

## Scope

This preflight prepares a private local destination for future synthetic Android HPROF captures under T015. It does not record a heap dump and does not establish full T045 completion.

## Destination and ACL

- Absolute destination: `C:\Users\octav\AppData\Local\Chimali\SEC065-heap`
- The directory is outside the repository and outside the normal app build output tree.
- Verified owner: `LOQ15IRX10-3\octav`.
- Inheritance is disabled. The only access rules are FullControl for that user and `NT AUTHORITY\SYSTEM`.
- No HPROF, expected-marker dictionary, or other capture file existed at preflight time.
- Captures must come only from the unique `com.chimali.heapaudit` process on the API 35 emulator. Do not capture production or personal-data process IDs.

## Synthetic state and handling

Each run will use a unique run ID and freshly generated synthetic inputs. The external expected-marker dictionary and raw HPROF files must remain under the restricted destination; publish only hashes, counts, object types, root classifications, and limitations in the repository. Do not copy raw HPROF or marker values into Git, Gradle reports, Logcat, or shared cloud folders.

Keep raw captures and the expected-marker dictionary accessible to the authorized independent reviewer while review depends on them. After review and any required follow-up are complete, delete raw captures and dictionaries, retain a disposal record and sanitized findings, then recheck the directory ACL. Because no reviewer or captures are currently registered, there is nothing to dispose yet and T045 remains open.

## Verification result

On 2026-11-17, PowerShell ACL inspection confirmed the protected directory, owner, disabled inheritance, and the two expected FullControl entries. The API 35 heapAudit target and test packages were removed after the lifecycle test. No raw heap capture has been made.
