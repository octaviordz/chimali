package com.chimali.feature.vault.ui.navigation

object VaultDestinations {
    const val LIST_ROUTE = "vault/list"
    const val ENTRY_PASSWORD_ROUTE = "vault/entry/password"
    const val ENTRY_CARD_ROUTE = "vault/entry/card"
    const val ENTRY_NOTE_ROUTE = "vault/entry/note"
    const val DETAIL_PASSWORD_ROUTE = "vault/detail/password/{id}"
    const val DETAIL_CARD_ROUTE = "vault/detail/card/{id}"
    const val DETAIL_NOTE_ROUTE = "vault/detail/note/{id}"
    const val LABELS_ROUTE = "vault/labels"

    fun detailRoute(
        type: com.chimali.feature.vault.api.VaultType,
        id: java.util.UUID,
    ): String =
        when (type) {
            com.chimali.feature.vault.api.VaultType.PASSWORD -> "vault/detail/password/$id"
            com.chimali.feature.vault.api.VaultType.CREDIT_CARD -> "vault/detail/card/$id"
            com.chimali.feature.vault.api.VaultType.NOTE -> "vault/detail/note/$id"
        }
}
