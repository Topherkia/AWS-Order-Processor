package com.example.aws_g4

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException

class SignInCancelledException : Exception("Sign-in cancelled")

/**
 * Microsoft Entra ID sign-in through MSAL. MSAL stores and refreshes the tokens;
 * the rest of the app only asks [currentToken] for a valid access token.
 *
 * Configuration lives in res/raw/auth_config.json (client id, tenant, redirect URI).
 */
object AuthManager {

    private const val API_SCOPE = "1ad91f42-1b69-4fa7-8b35-ed1b9536e97f"
    private val SCOPES = arrayOf(API_SCOPE)

    @Volatile
    private var app: ISingleAccountPublicClientApplication? = null

    /**
     * Creates the MSAL client (once) and reports the saved account's username,
     * or null when nobody is signed in.
     */
    fun init(context: Context, onAccount: (String?) -> Unit) {
        val existing = app
        if (existing != null) {
            loadAccount(existing, onAccount)
            return
        }
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context.applicationContext,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    app = application
                    loadAccount(application, onAccount)
                }

                override fun onError(exception: MsalException) {
                    android.util.Log.e("AuthManager", "MSAL init failed: ${exception.errorCode}", exception)
                    onAccount(null)
                }
            }
        )
    }

    private fun loadAccount(application: ISingleAccountPublicClientApplication, onAccount: (String?) -> Unit) {
        application.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) = onAccount(activeAccount?.username)
            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                if (currentAccount == null) onAccount(null)
            }
            override fun onError(exception: MsalException) = onAccount(null)
        })
    }

    /** Opens the Microsoft sign-in page. Reports the signed-in username. */
    fun signIn(activity: Activity, onResult: (Result<String>) -> Unit) {
        val application = app
        if (application == null) {
            onResult(Result.failure(IllegalStateException("MSAL is not initialised (check res/raw/auth_config.json)")))
            return
        }
        application.signIn(activity, null, SCOPES, object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                onResult(Result.success(authenticationResult.account.username))
            }

            override fun onError(exception: MsalException) {
                android.util.Log.e("AuthManager", "Sign-in failed: ${exception.errorCode}", exception)
                onResult(Result.failure(exception))
            }

            override fun onCancel() {
                onResult(Result.failure(SignInCancelledException()))
            }
        })
    }

    /**
     * A valid access token for the API, or null when nobody is signed in.
     * Called from the OkHttp interceptor (background thread), so the blocking silent call is fine.
     * MSAL returns the cached token or refreshes it when it is about to expire.
     */
    fun currentToken(): String? {
        val application = app ?: return null
        return try {
            val authority = application.configuration.defaultAuthority.authorityURL.toString()
            application.acquireTokenSilent(SCOPES, authority)?.accessToken
        } catch (e: Exception) {
            android.util.Log.w("AuthManager", "Silent token request failed", e)
            null
        }
    }

    fun signOut() {
        app?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {}
            override fun onError(exception: MsalException) {}
        })
    }

    /** User-facing text; the error code makes configuration problems easy to look up. */
    fun describe(e: Throwable): String {
        val code = (e as? MsalException)?.errorCode
        return if (code != null) "Sign-in failed ($code). Please try again." else "Sign-in failed. Please try again."
    }
}