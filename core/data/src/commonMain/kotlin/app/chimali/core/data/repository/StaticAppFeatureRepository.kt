/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.chimali.core.data.repository

import app.chimali.core.model.data.AppFeature
import app.chimali.core.model.data.AppFeatureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Disk storage backed implementation of the [AppFeatureRepository].
 * Reads are exclusively from local storage to support offline access.
 */
class StaticAppFeatureRepository : AppFeatureRepository {
    private val appFeatureList =
        listOf(
            AppFeature(
                id = AppFeatureId("9AEA6052-A7F5-40CD-BC58-067E1517D283"),
                name = "Authenticator",
                shortDescription = "FIDO2 / WebAuthn authenticator",
                longDescription = "Transform your device into a FIDO2 / WebAuthn authenticator. Verify your identity securely and access supported services without relying on traditional passwords.",
            ),
            AppFeature(
                id = AppFeatureId("73CFE645-2737-4C0C-A4F0-664E9C8EE6F9"),
                name = "Vault",
                shortDescription = "Protect your important information",
                longDescription = "Keep passwords, payment card details, and personal notes securely organized and readily available whenever you need them.",
            ),
        )

    override fun getAppFeatures(): Flow<List<AppFeature>> = flowOf(appFeatureList)

    override fun getAppFeature(name: String): Flow<AppFeature> = flowOf(appFeatureList.first { it.name == name })
}
