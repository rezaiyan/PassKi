# Module passki-demo

Client for the hosted PassKi demo relying party at https://passki.alirezaiyan.com, to try passkeys in your own app
before you have a domain or a WebAuthn server. **For evaluation only:** demo users expire after 24 hours.

Pair it with `passki { useDemoServer() }` in the Gradle plugin, which registers your app with the demo server.
[com.alirezaiyan.passki.demo.PasskiDemoServer] gets the WebAuthn options and returns the server's full verdict for
every response. Going to production means replacing it with calls to your own server; the PassKi calls stay the same.

# Package com.alirezaiyan.passki.demo

The demo server client and its result types.
