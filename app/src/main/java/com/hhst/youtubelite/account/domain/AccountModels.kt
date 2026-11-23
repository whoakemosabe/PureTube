package com.hhst.youtubelite.account.domain

enum class AccountMenuAction(val label: String, val tag: String) {
    ViewChannel("View channel", "view"),
    SwitchAccount("Switch account", "switch"),
    Settings("Settings", "settings"),
    About("About PureTube", "about"),
    SignOut("Sign out", "signout");
}

/** Leading icon for each row of the account menu. */
val AccountMenuAction.icon: com.hhst.youtubelite.ui.design.AppIcon
    get() = when (this) {
        AccountMenuAction.ViewChannel -> com.hhst.youtubelite.ui.design.AppIcon.You
        AccountMenuAction.SwitchAccount -> com.hhst.youtubelite.ui.design.AppIcon.Audience
        AccountMenuAction.Settings -> com.hhst.youtubelite.ui.design.AppIcon.Settings
        AccountMenuAction.About -> com.hhst.youtubelite.ui.design.AppIcon.Info
        AccountMenuAction.SignOut -> com.hhst.youtubelite.ui.design.AppIcon.Back
    }

data class AccountItem(val id: String, val name: String, val email: String, val photo: String, val selected: Boolean)

sealed interface SignInUiState {
    data object Loading : SignInUiState
    data class Ready(val userCode: String, val verificationUrl: String) : SignInUiState
    data object Completing : SignInUiState
    data object Success : SignInUiState
    data class Error(val message: String) : SignInUiState
}

sealed interface SignInAction {
    data object Retry : SignInAction
}
