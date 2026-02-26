package com.chimali.authenticator.di

import android.content.Context
import com.chimali.security.keystore.AndroidKeyStoreManager
import com.chimali.security.keystore.KeyStoreManager
import com.chimali.security.storage.AndroidSecureStorageManager
import com.chimali.security.storage.SecureStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideKeyStoreManager(): KeyStoreManager {
        return AndroidKeyStoreManager()
    }
    
    @Provides
    @Singleton
    fun provideSecureStorageManager(
        @ApplicationContext context: Context,
        keyStoreManager: KeyStoreManager
    ): SecureStorageManager {
        return AndroidSecureStorageManager(
            context = context,
            keyStoreManager = keyStoreManager
        )
    }
}
