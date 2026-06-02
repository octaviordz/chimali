package app.chimali.core.data.repository

import app.chimali.core.model.data.AppFeature
import kotlinx.coroutines.flow.Flow

interface AppFeatureRepository {
    /**
     * Gets the available features as a stream
     */
    fun getAppFeatures(): Flow<List<AppFeature>>

    /**
     * Gets data for a specific AppFeature
     */
    fun getAppFeature(name: String): Flow<AppFeature>
}
