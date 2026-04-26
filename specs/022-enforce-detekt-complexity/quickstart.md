# Quickstart: Enforce Detekt Complexity Rules

## Overview

This guide explains how to verify the new Detekt cognitive complexity rules.

## Validation Steps

1. **Run Local CI:**
   Execute the local CI pipeline to run Detekt across the entire project:
   ```powershell
   .\tools\local-ci.ps1
   ```
2. **Verify Output:**
   Ensure the output states `Ktlint Check passed` and `Detekt passed`. There should be no `CognitiveComplexMethod` failures.
3. **Run Unit Tests:**
   The CI pipeline will run all tests automatically to ensure no business logic was broken during refactoring.
