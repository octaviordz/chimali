package app.chimali.core.domain

import app.chimali.core.data.repository.AppFeatureRepository
import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.model.data.SelectableAppFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetSelectableAppFeatureUseCase(
    private val appFeatureRepository: AppFeatureRepository,
    private val userDataRepository: UserDataRepository,
) {
    operator fun invoke(): Flow<List<SelectableAppFeature>> =
        combine(
            userDataRepository.userData,
            appFeatureRepository.getAppFeatures(),
        ) { userData, appFeatures ->
            val selectedAppFeatures =
                appFeatures
                    .map { appFeature ->
                        SelectableAppFeature(
                            appFeature = appFeature,
                            isSelected = appFeature.id in userData.selectedAppFeatureIds,
                        )
                    }

            selectedAppFeatures.sortedBy { it.appFeature.name }
        }
}
