# Searchable metadata performance test fixture cleanup

`SearchableMetadataPerformanceTest` inserts 20,000 synthetic credential projections and 10,000 synthetic consent records into per-test in-memory SQLite. Each RP/user/token/metadata bind array is zeroed after the synchronous SQLDelight insert returns. Warm-up and measured query result BLOB projections are erased, lookup-token inputs are zeroed in `finally`, deterministic-token helper input bytes are cleared, and the JDBC driver closes in `@AfterEach`. The indexed lookup and batch-insert performance assertions remain unchanged.

Validation: all 3 focused performance cases pass and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passes. This host test does not prove SQLite page clearing or persistent/native driver behavior; the closed test database, immutable synthetic strings, heap and device/runtime copies remain separate concerns.
