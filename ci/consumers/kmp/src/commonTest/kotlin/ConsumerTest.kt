import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.test.FakePasskeys
import com.alirezaiyan.passki.test.TestOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumerTest {
    @Test
    fun registerAndSignInWithTheFake() = runTest {
        val passkeys = FakePasskeys(origin = "https://example.com")
        val registration = passkeys.register(TestOptions.creation("example.com")).getOrThrow()
        val assertion = passkeys.signIn(TestOptions.request("example.com")).getOrThrow()
        assertEquals(registration.credentialId, assertion.credentialId)
    }

    @Test
    fun thePlatformImplementationLoads() = runTest {
        // On iOS this links and loads the Swift bridge (Signal API), which needs the Swift runtime on the rpath.
        assertTrue(Passkeys().capabilities().toString().startsWith("PasskeyCapabilities("))
    }
}
