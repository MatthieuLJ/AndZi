package com.matthieu.zi_app.ui.badges

import com.matthieu.zi_app.domain.badges.BadgesSummary

data class BadgesUiState(
    val isLoading: Boolean = true,
    val summary: BadgesSummary? = null,
    val errorMessage: String? = null
)
