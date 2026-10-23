import Link from "next/link";
import { CodeTabs } from "@/components/CodeTabs";
import { HeroCheck } from "@/components/HeroCheck";
import { Kotlin } from "@/components/Kotlin";
import { CheckIcon, GitHubIcon, SkipIcon } from "@/components/Icons";
import { catalog } from "@/lib/catalog";

const REPO = "https://github.com/rezaiyan/PassKi";
const VERSION = "0.1.0-alpha02";

const QUICK_START = `val passkeys = Passkeys() // no Activity, no UIWindow

passkeys.register(api.registrationOptions())
    .onSuccess { api.finishRegistration(it.json) }
    .onFailure { error ->
        when (error) {
            PasskeyError.Cancelled -> Unit
            is PasskeyError.Misconfigured -> log(error.fixHint)
            else -> showError(error.message)
        }
    }

passkeys.signIn(api.authenticationOptions())
    .onSuccess { api.finishSignIn(it.json) }`;

const COMPOSE = `@Composable
fun SignInScreen(api: MyApi) {
    val scope = rememberCoroutineScope()
    val passkeys = rememberPasskeys()
    var username by remember { mutableStateOf("") }

    // Offers passkeys above the keyboard while focused
    PasskeyUsernameField(
        value = username,
        onValueChange = { username = it },
        requestOptions = { api.authenticationOptions() },
        onSignedIn = { scope.launch { api.finishSignIn(it.json) } },
        passkeys = passkeys,
    )
}`;

const TESTS = `// Real ES256 responses, accepted by webauthn4j
val passkeys = FakePasskeys(origin = "https://example.com")

passkeys.failNextWith(PasskeyError.Cancelled) // next call fails once
passkeys.conditionalCreate = false            // a device without upgrade

val options = TestOptions.creation("example.com")`;

const INSTALL = `commonMain.dependencies {
    implementation("com.alirezaiyan.passki:passki:${VERSION}")
    implementation("com.alirezaiyan.passki:passki-compose:${VERSION}")
}`;

const CALLS: [string, string][] = [
  ["register(options)", "Creates a passkey"],
  ["signIn(options, mode)", "Modal, device-only or keyboard autofill"],
  ["signInOrPassword(options)", "One sheet with passkeys and saved passwords"],
  ["upgrade(options)", "Adds a passkey without UI after a password sign-in"],
  ["signals", "Keeps password managers in sync with your server"],
  ["capabilities()", "What this device supports, before you show a button"],
];

const ROLES: { who: string; what: string; passki?: boolean }[] = [
  {
    who: "Your server",
    what: "Creates the options for each sign-up or sign-in, checks the signed response and stores the public key.",
  },
  {
    who: "PassKi",
    what: "Hands the options to the phone, returns the signed response as JSON, and tells you what to fix when setup is wrong.",
    passki: true,
  },
  {
    who: "The phone",
    what: "Shows the passkey sheet, asks for a face, fingerprint or screen lock, and saves the passkey in the user's password manager.",
  },
  {
    who: "Your domain",
    what: "Lists the apps allowed to use its passkeys in two .well-known files. PassKi's Gradle plugin writes and checks them.",
  },
];

type Doc = { title: string; href: string; about: string };
const DOC_GROUPS: { name: string; dot: string; docs: Doc[] }[] = [
  {
    name: "Standards",
    dot: "",
    docs: [
      {
        title: "Web Authentication, Level 3",
        href: "https://www.w3.org/TR/webauthn-3/",
        about: "W3C spec behind every passkey: options, responses, extensions",
      },
      {
        title: "Passkeys",
        href: "https://fidoalliance.org/passkeys/",
        about: "FIDO Alliance: what passkeys are and how they sync",
      },
      {
        title: "passkeys.dev",
        href: "https://passkeys.dev/",
        about: "FIDO and W3C community guides, device support, UX patterns",
      },
      {
        title: "Passkeys for developers",
        href: "https://developers.google.com/identity/passkeys",
        about: "Google: server-side registration and sign-in",
      },
    ],
  },
  {
    name: "Android",
    dot: "android",
    docs: [
      {
        title: "About passkeys",
        href: "https://developer.android.com/identity/passkeys",
        about: "Overview of passkeys on Android",
      },
      {
        title: "Credential Manager",
        href: "https://developer.android.com/identity/credential-manager",
        about: "The Jetpack API PassKi calls on Android",
      },
      {
        title: "Prerequisites",
        href: "https://developer.android.com/identity/credential-manager/prerequisites",
        about: "Dependencies and the Digital Asset Links your domain needs",
      },
      {
        title: "Create passkeys",
        href: "https://developer.android.com/identity/passkeys/create-passkeys",
        about: "Registration flow",
      },
      {
        title: "Sign in with passkeys",
        href: "https://developer.android.com/identity/passkeys/sign-in-with-passkeys",
        about: "Sign-in, autofill and passkey-or-password",
      },
      {
        title: "Signal API",
        href: "https://developer.android.com/identity/credential-manager/signal-api-rp",
        about: "Keep password managers in sync with your server",
      },
      {
        title: "Digital Asset Links",
        href: "https://developers.google.com/digital-asset-links/v1/getting-started",
        about: "The assetlinks.json format",
      },
      {
        title: "Troubleshooting",
        href: "https://developer.android.com/identity/sign-in/credential-manager-troubleshooting-guide",
        about: "Common Credential Manager errors",
      },
    ],
  },
  {
    name: "Apple",
    dot: "ios",
    docs: [
      {
        title: "Passkeys overview",
        href: "https://developer.apple.com/passkeys/",
        about: "Passkeys on Apple platforms",
      },
      {
        title: "Supporting passkeys",
        href: "https://developer.apple.com/documentation/authenticationservices/supporting-passkeys",
        about: "Registration and sign-in with AuthenticationServices",
      },
      {
        title: "Connecting to a service with passkeys",
        href: "https://developer.apple.com/documentation/authenticationservices/connecting-to-a-service-with-passkeys",
        about: "Apple's sample app",
      },
      {
        title: "Public-private key authentication",
        href: "https://developer.apple.com/documentation/authenticationservices/public-private-key-authentication",
        about: "The AuthenticationServices API family",
      },
      {
        title: "ASAuthorizationPlatformPublicKeyCredentialProvider",
        href: "https://developer.apple.com/documentation/authenticationservices/asauthorizationplatformpublickeycredentialprovider",
        about: "The class PassKi calls on iOS",
      },
      {
        title: "Supporting associated domains",
        href: "https://developer.apple.com/documentation/xcode/supporting-associated-domains",
        about:
          "The apple-app-site-association file and the webcredentials entitlement",
      },
      {
        title: "ASCredentialUpdater",
        href: "https://developer.apple.com/documentation/authenticationservices/ascredentialupdater",
        about: "Signals on iOS 26",
      },
    ],
  },
];

type Cell = string | false;
const MATRIX: [string, Cell, Cell][] = [
  ["Register and sign in", "9+", "16+"],
  ["Passkey or password in one sheet", "9+", "16+"],
  ["Keyboard autofill", "15+", "16+"],
  ["Upgrade after password sign-in", "9+", "18+"],
  ["Signals", "9+", "26+"],
  ["PRF", "Provider", "18+"],
  ["largeBlob", "Provider", "17+"],
];

function Phone({
  src,
  poster,
  label,
}: {
  src: string;
  poster: string;
  label: string;
}) {
  return (
    <figure className="device">
      <div className="device-frame">
        <video
          src={src}
          poster={poster}
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
          aria-label={label}
        />
      </div>
      <figcaption>{label}</figcaption>
    </figure>
  );
}

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="container hero-grid">
          <h1>Passkeys for Kotlin Multiplatform</h1>
          <p className="hero-lead">
            One call from <code>commonMain</code> on Android and iOS. Passkeys
            only appear when your app and your domain trust each other, so check
            that link before you ship.
          </p>
          <HeroCheck />
        </div>
      </section>

      <section className="strip" aria-label="Platforms">
        <div className="container strip-inner">
          <div>
            <b>Android 9+</b>
            <span>Credential Manager</span>
          </div>
          <div>
            <b>iOS 16+</b>
            <span>AuthenticationServices</span>
          </div>
          <div>
            <b>Compose Multiplatform</b>
            <span>Sign-in button and autofill field</span>
          </div>
          <div>
            <b>{catalog.checks.length} setup checks</b>
            <span>Gradle, device and web</span>
          </div>
          <div>
            <b>Apache 2.0</b>
            <span>Open source, no backend lock-in</span>
          </div>
        </div>
      </section>

      <section className="container band explain-band" id="passkeys">
        <div className="band-copy">
          <h2>What is a passkey?</h2>
          <p>
            A passkey replaces a password with a key pair. The private key stays
            in the user&apos;s password manager, such as Google Password Manager
            or iCloud Keychain. Your server keeps only the public key.
          </p>
          <ul className="ticks">
            <li>
              <CheckIcon size={14} />
              <span>
                <b>Nothing to remember.</b> Users unlock it with their face,
                fingerprint or screen lock.
              </span>
            </li>
            <li>
              <CheckIcon size={14} />
              <span>
                <b>Nothing to phish.</b> A passkey works only on the domain it
                was made for, so a fake site can&apos;t use it.
              </span>
            </li>
            <li>
              <CheckIcon size={14} />
              <span>
                <b>Nothing to leak.</b> A stolen public key can&apos;t sign
                anyone in.
              </span>
            </li>
          </ul>
        </div>
        <div className="panel roles">
          <div className="panel-head">
            <h3>What PassKi does</h3>
            <span className="muted small">Who does what in a sign-in</span>
          </div>
          <ol>
            {ROLES.map(({ who, what, passki }) => (
              <li key={who} className={passki ? "is-passki" : undefined}>
                <b>{who}</b>
                <span>{what}</span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="container band devices-band">
        <div className="band-copy">
          <h2>Real passkeys on real devices</h2>
          <p>
            The sample app registers a passkey and signs in with it, on Android
            16 with Google Password Manager and on iOS 26 with Face ID. The demo
            server verifies every response.
          </p>
          <ul className="ticks">
            <li>
              <CheckIcon size={14} />
              <span>
                Every failure is a typed <code>PasskeyError</code>. Setup
                problems carry a <code>fixHint</code>.
              </span>
            </li>
            <li>
              <CheckIcon size={14} />
              <span>
                No <code>Activity</code>, no <code>UIWindow</code>: call it from
                shared code.
              </span>
            </li>
            <li>
              <CheckIcon size={14} />
              <span>
                Your server stays yours. PassKi never sees it and keeps no
                state.
              </span>
            </li>
          </ul>
          <Link className="button secondary" href="/demo">
            Use the demo server
          </Link>
        </div>
        <div className="devices">
          <Phone
            src="/media/android-register-sign-in.mp4"
            poster="/media/android-register-sign-in.jpg"
            label="Android 16 · Google Password Manager"
          />
          <Phone
            src="/media/ios-register-sign-in.mp4"
            poster="/media/ios-register-sign-in.jpg"
            label="iOS 26 · Face ID"
          />
        </div>
      </section>

      <section className="container band code-band">
        <div className="band-copy">
          <h2>One API in commonMain</h2>
          <p>
            Your server creates the WebAuthn options and verifies the result.
            PassKi turns them into the native sheet on each platform.
          </p>
          <dl className="calls">
            {CALLS.map(([call, what]) => (
              <div key={call}>
                <dt>
                  <code>{call}</code>
                </dt>
                <dd>{what}</dd>
              </div>
            ))}
          </dl>
        </div>
        <CodeTabs
          tabs={[
            {
              label: "Quick start",
              file: "SignIn.kt",
              content: <Kotlin code={QUICK_START} />,
            },
            {
              label: "Compose",
              file: "SignInScreen.kt",
              content: <Kotlin code={COMPOSE} />,
            },
            {
              label: "Tests",
              file: "SignInTest.kt",
              content: <Kotlin code={TESTS} />,
            },
          ]}
        />
      </section>

      <section className="container band-stack">
        <div className="section-head">
          <h2>What works where</h2>
          <p>
            Minimum OS versions. <code>capabilities()</code> tells your UI the
            same thing at runtime.
          </p>
        </div>
        <div className="panel table-wrap">
          <table className="matrix">
            <thead>
              <tr>
                <th scope="col">Feature</th>
                <th scope="col">Android</th>
                <th scope="col">iOS</th>
                <th scope="col">JVM desktop, browser</th>
              </tr>
            </thead>
            <tbody>
              {MATRIX.map(([feature, android, ios]) => (
                <tr key={feature}>
                  <th scope="row">{feature}</th>
                  <td>
                    <span className="cap yes">
                      <CheckIcon size={12} /> {android}
                    </span>
                  </td>
                  <td>
                    <span className="cap yes">
                      <CheckIcon size={12} /> {ios}
                    </span>
                  </td>
                  <td>
                    <span className="cap no">
                      <SkipIcon size={12} /> Compiles
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted small matrix-note">
          &ldquo;Provider&rdquo;: passed to the credential provider; Google
          Password Manager supports it. JVM and browser targets compile so
          shared code builds everywhere, and return{" "}
          <code>PasskeyError.NotSupported</code> for now.
        </p>
      </section>

      <section className="container band-stack">
        <div className="section-head">
          <h2>Setup that checks itself</h2>
          <p>
            Most passkey bugs are configuration. The same{" "}
            {catalog.checks.length} check IDs run in your build, on the device
            and here.
          </p>
        </div>
        <div className="bento">
          <article className="tile tile-gradle">
            <div className="tile-copy">
              <h3>Gradle plugin</h3>
              <p>
                Writes both <code>.well-known</code> files, wires iOS
                entitlements through xcconfig, then checks the live domain.
                Fails CI on any error.
              </p>
              <ol className="cmds">
                <li>
                  <code>./gradlew passkiSetup</code>
                  <span>generate and wire</span>
                </li>
                <li>
                  <code>./gradlew passkiCheck</code>
                  <span>check the live domain</span>
                </li>
              </ol>
            </div>
            <img
              className="tile-shot"
              src="/media/passki-check-report.png"
              alt="passkiCheck HTML report listing each check with its ID and fix"
              width={900}
              height={868}
              loading="lazy"
              decoding="async"
            />
          </article>
          <article className="tile tile-doctor">
            <h3>PasskeyDoctor</h3>
            <p>
              The same checks from inside a debug build, using the app&apos;s
              real signature.
            </p>
            <div className="mini-phone">
              <img
                src="/media/android-setup-check.png"
                alt="The sample's Setup tab on Android: ready for passkeys, 8 checks passed, each with its catalog ID"
                width={400}
                height={898}
                loading="lazy"
                decoding="async"
              />
            </div>
          </article>
          <Link className="tile tile-link tile-verify" href="/verify">
            <h3>Web verifier</h3>
            <p>
              Fetches your files, Google Digital Asset Links and Apple&apos;s
              CDN from our server. Share a read-only report with your team.
            </p>
            <span className="go">Verify a domain</span>
          </Link>
          <Link className="tile tile-link tile-gen" href="/generator">
            <h3>Generator</h3>
            <p>
              No Gradle yet? Paste fingerprints and IDs, download every file as
              a zip.
            </p>
            <span className="go">Generate files</span>
          </Link>
        </div>
      </section>

      <section className="container band-stack" id="docs">
        <div className="section-head">
          <h2>Official passkey docs</h2>
          <p>
            The specs and platform guides PassKi is built on. Read them for the
            details behind an API or a setup check.
          </p>
        </div>
        <div className="docs-grid">
          {DOC_GROUPS.map(({ name, dot, docs }) => (
            <div key={name} className="panel docs-group">
              <h3>
                <span className={`platform-dot ${dot}`} />
                {name}
              </h3>
              <ul className="doc-list">
                {docs.map(({ title, href, about }) => (
                  <li key={href}>
                    <a href={href}>
                      <b>{title}</b>
                      <span>{about}</span>
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      <section className="install">
        <div className="container install-inner">
          <div>
            <h2>Add it to your shared module</h2>
            <p>
              Apache 2.0. Works with java-webauthn-server, webauthn4j,
              SimpleWebAuthn and py_webauthn on the server.
            </p>
            <div className="actions">
              <a className="button" href={`${REPO}#install`}>
                <GitHubIcon /> Read the guide
              </a>
              <Link className="button secondary" href="/generator">
                Set up your domain
              </Link>
            </div>
          </div>
          <Kotlin code={INSTALL} />
        </div>
      </section>
    </>
  );
}
