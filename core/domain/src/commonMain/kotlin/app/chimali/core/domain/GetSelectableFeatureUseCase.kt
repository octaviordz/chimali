package app.chimali.core.domain

import app.chimali.core.model.data.SelectableFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
// import kotlinx.coroutines.flow.flow

class GetSelectableFeatureUseCase {
    operator fun invoke(): Flow<List<SelectableFeature>> =
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
                SelectableFeature(name = "Authenticator", isSelected = false),
                SelectableFeature(name = "Vault", isSelected = false),
            ),
        )
}
