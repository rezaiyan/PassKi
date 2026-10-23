package snippets

import com.alirezaiyan.passki.Passkeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// What the docs' examples take for granted: your server API, a coroutine scope, logging and some inputs. `MyApi` itself
// comes from the README's Quick start, so the examples compile against the interface readers see.

lateinit var api: MyApi
val passkeys: Passkeys get() = Passkeys()
val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
val salt: ByteArray = ByteArray(32)
val blob: ByteArray = ByteArray(16)
val creationOptions: String = "{}"
val requestOptions: String = "{}"

fun log(vararg values: Any?) = println(values.joinToString(" "))

fun showError(message: String) = println(message)

object BuildConfig {
    const val DEBUG: Boolean = true
}
