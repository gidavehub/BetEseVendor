package com.betesepmu.vendor.data

import android.content.Context
import com.betesepmu.vendor.model.VendorUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Custom username/password auth against the Firestore `users` collection — the exact
 * mechanism the betesepmu web app uses (see `firebaseClient.ts` `dbFindUser` + the password
 * check at line 749). There is **no** Firebase Auth here; staff sign in with the same
 * credentials as on the website. The session is persisted so the terminal stays logged in
 * across restarts.
 */
class AuthRepository(
    context: Context,
    private val firestore: FirebaseFirestore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(loadSession())
    val currentUser: StateFlow<VendorUser?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean get() = _currentUser.value != null

    /**
     * Look up [identifier] (username / phone / id) in `users`, verify the account may use the
     * terminal, and compare the plaintext password. On success the user is cached + persisted.
     */
    suspend fun login(identifier: String, password: String): Result<VendorUser> {
        val id = identifier.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Enter your username."))
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("Enter your password."))

        val doc = runCatching { findUserDoc(id) }
            .getOrElse { return Result.failure(Exception("Network error. Check the connection and try again.")) }
            ?: return Result.failure(Exception("No account found for \"$id\"."))

        val role = doc.str("role") ?: "Vendor"
        if (role.equals("Customer", ignoreCase = true)) {
            return Result.failure(Exception("Customer accounts cannot sign in to the vendor terminal."))
        }
        if (anyToBool(doc.get("is_locked"))) {
            return Result.failure(Exception("This account is locked. Contact your supervisor."))
        }
        val storedPassword = (doc.str("password") ?: "").trim()
        if (storedPassword != password.trim()) {
            return Result.failure(Exception("Incorrect password."))
        }

        val user = doc.toVendorUser()
            ?: return Result.failure(Exception("Account record is incomplete."))
        setUser(user)
        return Result.success(user)
    }

    /** Re-read the signed-in user's doc to refresh wallet/lock state. No-op if logged out. */
    suspend fun refresh() {
        val id = _currentUser.value?.id ?: return
        val fresh = runCatching { firestore.collection(USERS).document(id).get().await().toVendorUser() }
            .getOrNull() ?: return
        if (fresh.isLocked) logout() else setUser(fresh)
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    // ---- internals ---------------------------------------------------------

    private suspend fun findUserDoc(identifier: String): DocumentSnapshot? {
        val users = firestore.collection(USERS)
        val lower = identifier.lowercase()

        users.whereEqualTo("name_lower", lower).limit(1).get().await()
            .documents.firstOrNull()?.let { return it }
        users.whereEqualTo("name", identifier).limit(1).get().await()
            .documents.firstOrNull()?.let { return it }
        users.whereEqualTo("phone", identifier).limit(1).get().await()
            .documents.firstOrNull()?.let { return it }

        val byId = users.document(identifier).get().await()
        return if (byId.exists()) byId else null
    }

    private fun setUser(user: VendorUser) {
        _currentUser.value = user
        prefs.edit()
            .putString(KEY_ID, user.id)
            .putString(KEY_NAME, user.name)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_PHONE, user.phone)
            .putString(KEY_WALLET, user.walletBalance.toString())
            .putString(KEY_BONUS, user.bonusBalance.toString())
            .apply()
    }

    /** Rebuild the cached user from prefs on cold start; balances refresh on next [refresh]. */
    private fun loadSession(): VendorUser? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return VendorUser(
            id = id,
            name = prefs.getString(KEY_NAME, "") ?: "",
            role = prefs.getString(KEY_ROLE, "Vendor") ?: "Vendor",
            phone = prefs.getString(KEY_PHONE, null),
            walletBalance = prefs.getString(KEY_WALLET, "0")?.toDoubleOrNull() ?: 0.0,
            bonusBalance = prefs.getString(KEY_BONUS, "0")?.toDoubleOrNull() ?: 0.0,
        )
    }

    private companion object {
        const val PREFS = "betese_vendor_session"
        const val USERS = "users"
        const val KEY_ID = "user_id"
        const val KEY_NAME = "user_name"
        const val KEY_ROLE = "user_role"
        const val KEY_PHONE = "user_phone"
        const val KEY_WALLET = "user_wallet"
        const val KEY_BONUS = "user_bonus"
    }
}
