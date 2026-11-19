# Keep the direct performance-log sink callable by the release-derived instrumentation probe.
# The rest of the target remains minified/optimized; this target is test-only and not shippable.
-keep class com.chimali.fido2.util.performance.BaselineMeasurements { *; }
