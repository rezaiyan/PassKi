# Spec Delta

## ADDED Requirements

### Requirement: Stateless sign-in button
`passki-compose` SHALL provide `PasskeySignInButton(onClick, modifier, isLoading, enabled, content)` that only renders: it calls `onClick`, shows a progress indicator and is disabled while `isLoading` is true, and starts no sign-in itself. Its default content MUST be the passkey icon followed by "Sign in with a passkey".

#### Scenario: Driven by a ViewModel
- **WHEN** an app passes `onClick = viewModel::signInWithPasskey` and `isLoading = state.loading`
- **THEN** tapping calls the ViewModel, and the button shows progress while `state.loading` is true

#### Scenario: Custom design system
- **WHEN** an app supplies its own `content`
- **THEN** the button renders that content and keeps the loading and enabled behaviour

### Requirement: Passkey icon
`passki-compose` SHALL expose the passkey symbol as a public `ImageVector` (`PasskiIcons.Passkey`) usable in any composable, tinted by the caller.

#### Scenario: Icon in an app's own button
- **WHEN** an app draws `Icon(PasskiIcons.Passkey, contentDescription = null)` in its own button
- **THEN** the passkey symbol renders in the current content colour
