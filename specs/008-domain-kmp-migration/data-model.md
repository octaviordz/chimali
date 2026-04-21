# Data Model: KMP Domain Module

## Structural Entities

### UseCase (Interface/Abstract Class)
- **Purpose**: Base for all domain business logic units.
- **Location**: `commonMain`
- **Mechanism**: Coroutine-based `invoke()` function.

### DomainModel (Data Class)
- **Purpose**: Platform-agnostic data representation.
- **Location**: `commonMain`
- **Constraint**: Must be serializable (if needed) and free of platform imports.
