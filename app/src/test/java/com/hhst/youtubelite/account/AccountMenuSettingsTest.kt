package com.hhst.youtubelite.account

import com.hhst.youtubelite.account.domain.AccountMenuAction
import com.hhst.youtubelite.account.domain.icon
import com.hhst.youtubelite.account.presentation.accountMenuActions
import com.hhst.youtubelite.home.domain.HomeNavigationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Settings moved to a gear on Home and the Me tab; the account menu keeps the rest. */
class AccountMenuSettingsTest {
    @Test fun accountMenuKeepsEverythingButSettings() {
        assertFalse(AccountMenuAction.Settings in accountMenuActions)
        assertEquals(listOf(AccountMenuAction.SwitchAccount, AccountMenuAction.About, AccountMenuAction.SignOut),
            accountMenuActions)
    }

    @Test fun everyAccountRowHasAnIcon() {
        assertEquals(AccountMenuAction.entries.size, AccountMenuAction.entries.map { it.icon }.toSet().size)
    }

    @Test fun homeHasASettingsDestination() {
        assertEquals("Settings", HomeNavigationRequest.Settings.toString())
    }
}
