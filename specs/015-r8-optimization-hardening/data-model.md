# Data Model: R8 Configuration & Keep Rule Hierarchy

## Build Type Schema

| Build Type | Minify | Shrink Res | Signing | Purpose |
|------------|--------|------------|---------|---------|
| `debug` | `false` | `false` | `debug` | Standard development |
| `shrunkDebug`| `true` | `true` | `debug` | Local R8 verification |
| `release` | `true` | `true` | `release`| Production deployment |

## Keep Rule Hierarchy

### Level 1: Global Rules (proguard-android-optimize.txt)
- Standard Android platform rules.
- Optimization of common Java/Kotlin patterns.

### Level 2: Project Settings (gradle.properties)
- `android.r8.strictFullModeForKeepRules=true`
- `android.r8.optimizedResourceShrinking=true`

### Level 3: Feature-Specific Rules (proguard-rules.pro)
- **Serialization**: Narrow rules for `@Serializable` metadata and named companion objects.
- **JNI/Native**: Mandatory preservation of `native` method names and their containing classes.
- **DI (Koin)**: Preservation of Koin module definitions and annotated components (if reflection is used).

## Keep Rule Cleanup Logic

| Source | Status | Cleanup Action |
|--------|--------|----------------|
| `android.**` | Redundant | Remove (Handled by platform/SDK) |
| `kotlinx.coroutines.**` | Redundant | Remove (Bundled in library) |
| `androidx.**` | Redundant | Remove (Bundled in library) |
| `org.bouncycastle.**` | Redundant | Remove (Bundled in library) |
| `dagger.hilt.**` | Obsolete | Remove (Project uses Koin) |
| `com.chimali.fido2.**` | Overly Broad | Refine to specific packages/classes |
