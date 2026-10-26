package snippets

import com.alirezaiyan.passki.Passkeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// What the docs' examples take for granted: your server API, a coroutine scope, logging and some inputs. `MyApi` itself
// comes from the README's Usage section, so the examples compile against the interface readers see.

lateinit var api: MyApi
val passkeys: Passkeys get() = Passkeys()
val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
val salt: ByteArray = ByteArray(32)
val blob: ByteArray = ByteArray(16)
val creationOptions: String = "{}"
val requestOptions: String = "{}"

fun log(vararg values: Any?) = println(values.joinToString(" "))

fun showError(message: String) = println(message)

fun showMessage(message: String) = println(message)

// What an app's API client throws when the server has no such passkey.
class UnknownPasskeyException : Exception()

// Your server's endpoints, as server tests call them.
interface MyServer {
    suspend fun registrationOptions(user: String): String
    suspend fun finishRegistration(user: String, responseJson: String)
    suspend fun authenticationOptions(): String
    suspend fun finishSignIn(responseJson: String)
}

lateinit var server: MyServer
val user: String = "alice"

object BuildConfig {
    const val DEBUG: Boolean = true
}

// The logging libraries the README's handler examples route to, with the signatures of Timber 5, Kermit 2 and
// Firebase Crashlytics.
object Timber {
    class Tree {
        fun e(t: Throwable?, message: String?) = println(message)
        fun w(t: Throwable?, message: String?) = println(message)
        fun d(t: Throwable?, message: String?) = println(message)
    }

    fun tag(tag: String): Tree = Tree()
}

enum class Severity { Debug, Error }

object Logger {
    fun log(severity: Severity, tag: String, throwable: Throwable?, message: String) = println("$severity $tag $message")
}

class FirebaseCrashlytics {
    fun log(message: String) = println(message)
}

object Firebase {
    val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics()
}
