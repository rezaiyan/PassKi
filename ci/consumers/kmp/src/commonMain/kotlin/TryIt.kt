import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.demo.PasskiDemoServer
import com.alirezaiyan.passki.onFailure
import com.alirezaiyan.passki.onSuccess

/** The README's "try it in your app" code, plus the Usage error handling. */
suspend fun tryIt(passkeys: Passkeys = Passkeys()) {
    val demo = PasskiDemoServer()
    passkeys.register(demo.registrationOptions("alice"))
        .onSuccess { println(demo.verifyRegistration(it.json)) }
        .onFailure { error -> if (error is PasskeyError.Misconfigured) println("${error.fixHint} ${error.docsUrl}") }
    passkeys.signIn(demo.authenticationOptions())
        .onSuccess { println(demo.verifyAuthentication(it.json)) }
}
