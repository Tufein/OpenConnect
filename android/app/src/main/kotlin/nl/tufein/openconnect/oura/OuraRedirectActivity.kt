package nl.tufein.openconnect.oura

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import nl.tufein.openconnect.MainActivity
import nl.tufein.openconnect.OpenConnectApp

/**
 * Oura sends the browser back to openconnect://oura?code=…&state=… after the login. This screen has no face: it hands
 * the address to the Oura link (which checks the state and trades the code for tokens) and brings the app back.
 */
class OuraRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { (application as OpenConnectApp).container.oura.finishLogin(it) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
