# LeakCanary's instrumentation assertion APK calls this public API in the target app.
-keep class leakcanary.ObjectWatcher { *; }

# App-process instrumentation calls these public factories from a separate APK.
# R8 otherwise removes factory overloads that are only referenced by androidTest.
-keep class com.chimali.fido2.domain.model.** { *; }
-keep interface com.chimali.fido2.domain.service.Fido2Service { *; }
-keep class com.chimali.fido2.domain.service.impl.Fido2ServiceImpl { *; }
-keep class com.chimali.fido2.domain.usecase.GetAssertionUseCase { *; }
-keep interface com.chimali.fido2.domain.repository.CredentialRepository { *; }
-keep interface com.chimali.core.security.api.MasterSeedProvider { *; }
-keep class com.chimali.core.common.result.OutcomeKt { *; }
