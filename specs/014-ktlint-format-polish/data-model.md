# Style Guide: Kotlin Formatting Rules

## Core Rules (.editorconfig)

| Rule | Value | Description |
| :--- | :--- | :--- |
| `indent_style` | `space` | Use spaces for indentation. |
| `indent_size` | `4` | Standard 4-space indentation. |
| `continuation_indent_size` | `8` | 8-space indent for wrapped lines. |
| `max_line_length` | `120` | Soft limit for line width. |
| `insert_final_newline` | `true` | Always end files with a newline. |
| `ij_kotlin_imports_layout` | `*,java.**,javax.**,kotlin.**,kotlinx.**` | Standard import ordering layout. |

## Specialized Constraints

### Comment Retention
- **Requirement**: No automated formatting may delete or mangle comments.
- **Verification**: Post-formatting diff review.

### Semantic Preservation
- **Requirement**: Zero logic changes.
- **Verification**: Bytecode/Binary diff or full test suite execution.

## Exclusions
- `**/build/**`: Exclude all generated build artifacts.
- `**/.sqldelight/**`: Exclude SqlDelight metadata.
- `**/generated/**`: Exclude KSP/BuildKonfig generated source code.
