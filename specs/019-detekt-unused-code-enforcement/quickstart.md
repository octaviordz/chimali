# Quickstart: Unused Code Enforcement

This guide explains how to handle Detekt violations for `UnusedPrivateMember` and `UnusedPrivateProperty` following the new project-wide enforcement.

## 1. Finding Violations

Run the local CI pipeline to check for violations:

```powershell
./tools/local-ci.ps1
```

Or run Detekt directly:

```powershell
./gradlew detekt
```

## 2. Handling a Violation

When Detekt flags an unused private member/property:

### Option A: Remove (Preferred)
If the code is truly dead, delete it.

### Option B: Suppress (Exception)
If the code must be kept (e.g., for reflection or future integration):

1.  Add a comment explaining **why** it is being kept.
2.  Add the `@Suppress` annotation.

```kotlin
// Kept for future reflection-based UI binding in v2
@Suppress("UnusedPrivateProperty")
private val futureBindingId = "binding_v2"
```

## 3. Excluded Paths

The following paths are automatically ignored by these rules:
- `**/build/**`
- `**/generated/**`
