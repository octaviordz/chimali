package app.chimali.core.domain

import app.chimali.core.data.repository.AppFeatureRepository
import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.model.data.SelectableAppFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
// import kotlinx.coroutines.flow.flow

class GetSelectableAppFeatureUseCase(
    private val appFeatureRepository: AppFeatureRepository,
    private val userDataRepository: UserDataRepository,
) {
    operator fun invoke(): Flow<List<SelectableAppFeature>> =
//        flow {
//            emit(
//                listOf(
//                    SelectableFeature(name = "Authenticator", isSelected = false),
//                    SelectableFeature(name = "Vault", isSelected = false),
//                ),
//            )
//        }
        flowOf(
            listOf(
                SelectableAppFeature(name = "Authenticator", isSelected = false),
                SelectableAppFeature(name = "Vault", isSelected = false),
            ),
        )
}
