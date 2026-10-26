# Design

## Context

- `PasskeySignInState` (`passki-compose`) implements the single-flight logic with a `Mutex` and a
  `CompletableDeferred`, and exposes `isRunning` / `lastError` as Compose `mutableStateOf`. Its behaviour is specified
  in `compose-ui` "Headless sign-in state".
- `passki` depends on `kotlinx-coroutines-core` as `implementation`; `passki-test` already exposes it as `api`.
- `PasskeySignInButton` is a Material 3 `Button` taking a `PasskeySignInState`.

## Goals / Non-Goals

**Goals:**
- The same guard for ViewModels and composables, implemented once.
- A button any design system can use.

**Non-Goals:**
- A controller for `register` (registration usually runs once from Settings; revisit if asked).
- Platform-specific ViewModel integrations (androidx `ViewModel` helpers).

## Decisions

1. **Move the logic, keep the Compose API.** `PasskeySignInController` takes over the `Mutex` / `Deferred` code;
   `PasskeySignInState` wraps a controller and mirrors its flows into Compose state, so the `compose-ui` spec keeps
   holding unchanged.
2. **`StateFlow` in core.** Reactive state is what MVVM apps consume; it requires `kotlinx-coroutines-core` as `api`.
   That library is already on every KMP app's classpath, so the cost is small. Alternative: callbacks, rejected as
   awkward for ViewModels.
3. **No scope parameter.** `signIn()` is `suspend` and runs in the caller's scope (a ViewModel's `viewModelScope`);
   the "starter cancelled → joiners get `Cancelled`" rule is kept from the existing state class.
4. **Stateless button first.** The stateful `PasskeySignInButton(state, onSignedIn, …)` becomes a wrapper around the
   stateless overload; both stay Material 3 by default, and the stateless one accepts any `content`.
5. **Icon as code.** Ship the passkey symbol as an `ImageVector` built in Kotlin (no resources), after confirming the
   FIDO Alliance usage terms allow redistribution; if they don't, ship a neutral key glyph under the same name.

## Risks / Trade-offs

- [Behaviour drift between controller and state] → The existing `PasskeySignInState` tests run unchanged against the
  wrapper.
- [`api` coroutines dependency pins a version on adopters] → Use the version already required by the Kotlin 2.2
  toolchain policy in the README.

## Migration Plan

Additive. ViewModels replace their own guard with a controller; Compose users change nothing.
