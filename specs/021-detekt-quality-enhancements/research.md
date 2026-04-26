# Research & Decisions: Detekt Quality Enhancements

## Decision: Retain Current Complexity Thresholds
- **Decision**: Retain `CognitiveComplexMethod` threshold at 100 and `CyclomaticComplexMethod` at 40.
- **Rationale**: The user explicitly requested to keep current project thresholds while focusing on the "Zero-Exclusion" goal.
- **Alternatives considered**: Tightening to industry standards (15-20). This was rejected to avoid a massive refactor of legacy code in a single PR, prioritizing consistency over immediate idealization.

## Decision: Global Removal of Module-Level Exclusions
- **Decision**: Purge all path-based exclusions for `feature:vault` and `feature:fido2` across all rules.
- **Rationale**: Feature modules are security-critical and should be subject to the same quality gates as the rest of the production code. 
- **Alternatives considered**: Targeted removal of exclusions. Rejected in favor of a "clean slate" approach to quality for feature modules.

## Decision: String Literal Duplication Threshold
- **Decision**: Set `StringLiteralDuplication` threshold to **5** occurrences.
- **Rationale**: Align with the project's current configuration and user preference for reducing false positives in UI/API code.
- **Alternatives considered**: Setting a stricter threshold of 3. Rejected as it might force extraction of common placeholders or keys that don't benefit from constant extraction.

## Decision: Raw String Escape Counting
- **Decision**: `maxEscapedCharacterCount` logic will ignore simple escapes (`\n`, `\t`, `\r`) and only count complex ones (`\"`, `\$`, `\\`).
- **Rationale**: Simple formatting escapes are often cleaner in a single-line string for short multi-line messages, whereas complex escapes significantly hinder readability and are the primary reason for switching to Raw Strings.
- **Alternatives considered**: Counting all escapes. Rejected as it would be too aggressive for simple multi-line strings.
