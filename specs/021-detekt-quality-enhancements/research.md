# Research: Detekt Quality Enhancements

## Decisions

### 1. Logging Discipline (ForbiddenMethodCall)
- **Decision**: Enable `ForbiddenMethodCall` globally but apply a source-set exclusion for `test` and `androidTest`.
- **Rationale**: `println` is often used for quick debugging or log-streaming in unit tests, where a full structured logger might be overkill. However, it is strictly prohibited in production code to ensure all logs go through the project's approved logging library.
- **Alternatives considered**: Using `ForbiddenImport` for `kotlin.io.println`, but `ForbiddenMethodCall` is more precise for function invocations.

### 2. Idiomatic Scope Functions (UnnecessaryLet & UseLet)
- **Decision**: Enable both rules with default configurations.
- **Rationale**: These rules align with Principle III of the Chimali Constitution (Architecture & Quality). They reduce the "let-nesting" anti-pattern and encourage using simple null-checks or direct calls when a scope function adds no value.
- **Alternatives considered**: Manual code review, but automation ensures 100% consistency across the team.

### 3. String Literal Duplication Threshold
- **Decision**: Set the initial threshold to **3 occurrences** and ignore strings with **less than 5 characters**.
- **Rationale**: A threshold of 2 is often too noisy (e.g., "id", "name"), while 3 strikes a good balance between identifying "magic strings" and allowing for incidental repetition. Ignoring short strings prevents flagging common identifiers or empty strings.
- **Alternatives considered**: Setting a threshold of 5, but that might let significant duplication slip through.

## References
- [Detekt: ForbiddenMethodCall](https://detekt.dev/docs/rules/style#forbiddenmethodcall)
- [Detekt: StringLiteralDuplication](https://detekt.dev/docs/rules/complexity#stringliteralduplication)
- [Kotlin Idioms: Scope Functions](https://kotlinlang.org/docs/scope-functions.html)
