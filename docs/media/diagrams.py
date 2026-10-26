#!/usr/bin/env python3
"""Draws the README's diagrams as SVG, in a light and a dark version (the README picks one with <picture>).

Usage: python3 docs/media/diagrams.py   (writes docs/media/<diagram>-{light,dark}.svg)
"""
import os
from html import escape

HERE = os.path.dirname(os.path.abspath(__file__))
SANS = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

STEP = 2  # seconds each step of the sign-in diagram stays highlighted

THEMES = {
    "light": dict(card="#f6f8fa", stroke="#d0d7de", text="#1f2328", muted="#59636e", arrow="#8c959f",
                  server="#1a7f37", passki="#7f52ff", os="#0969da", domain="#bc4c00", code="#eaeef2",
                  screen="#ffffff", field="#eaeef2", sheet="#ffffff", button="#1f2328", on_button="#ffffff",
                  term="#1f2328"),
    "dark": dict(card="#161b22", stroke="#30363d", text="#e6edf3", muted="#9198a1", arrow="#6e7681",
                 server="#3fb950", passki="#a78bfa", os="#4493f8", domain="#f0883e", code="#21262d",
                 screen="#0d1117", field="#21262d", sheet="#1c2128", button="#e6edf3", on_button="#0d1117",
                 term="#010409"),
}


def text(x, y, value, size, fill, weight="normal", family=SANS, anchor="start"):
    space = ' xml:space="preserve"' if family == MONO else ""  # code keeps its indentation
    return (f'<text x="{x}" y="{y}" font-family="{family}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}"{space}>{escape(value)}</text>')


def lines(x, y, values, size, fill, gap, **kw):
    return "".join(text(x, y + i * gap, v, size, fill, **kw) for i, v in enumerate(values))


# Motion is CSS inside the SVG (GitHub shows SVGs as images: no scripts, but CSS animations run). It stays subtle,
# loops slowly, and switches off for people who ask for reduced motion; the static frame reads on its own.
REDUCED_MOTION = "@media (prefers-reduced-motion: reduce) { * { animation: none !important; } }"


def svg(width, height, body, title, style=""):
    css = f"<style>{style}{REDUCED_MOTION}</style>" if style else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}" role="img" aria-label="{escape(title)}">'
            f"<title>{escape(title)}</title>{css}{body}</svg>\n")


def arrow(x1, y1, x2, y2, color, head=7):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2 - head}" y2="{y2}" stroke="{color}" stroke-width="2"/>'
            f'<path d="M{x2 - head} {y2 - 5} L{x2} {y2} L{x2 - head} {y2 + 5} Z" fill="{color}"/>')


def how_it_works(t):
    steps = [
        ("server", "YOUR SERVER", "Create options", ["A fresh challenge for this", "sign-in, as WebAuthn JSON."],
         "POST /sign-in-options"),
        ("passki", "PASSKI", "Show the sheet", ["One call from commonMain.", "No Activity, no UIWindow."],
         "passkeys.signIn(options)"),
        ("os", "THE DEVICE", "User confirms", ["Face, fingerprint or screen", "lock. The device signs."],
         "Android · iOS · web"),
        ("server", "YOUR SERVER", "Verify, sign in", ["Signature, challenge, origin", "and RP ID, then a session."],
         "POST /sign-in  (it.json)"),
    ]
    card_w, gap, top = 196, 32, 44
    width = 2 * 12 + 4 * card_w + 3 * gap
    body = []
    for i, (kind, who, title, desc, code) in enumerate(steps):
        x = 12 + i * (card_w + gap)
        color = t[kind]
        body.append(text(x + 4, top - 14, who, 11, color, weight="700"))
        body.append(f'<rect x="{x}" y="{top}" width="{card_w}" height="168" rx="12" fill="{t["card"]}" '
                    f'stroke="{t["stroke"]}"/>')
        body.append(f'<rect x="{x}" y="{top}" width="{card_w}" height="4" rx="2" fill="{color}"/>')
        body.append(f'<circle cx="{x + 30}" cy="{top + 38}" r="15" fill="{color}"/>')
        body.append(text(x + 30, top + 43, str(i + 1), 14, "#ffffff", weight="700", anchor="middle"))
        body.append(text(x + 54, top + 43, title, 15, t["text"], weight="600"))
        body.append(lines(x + 16, top + 80, desc, 12.5, t["muted"], 18))
        body.append(f'<rect x="{x + 12}" y="{top + 122}" width="{card_w - 24}" height="28" rx="6" fill="{t["code"]}"/>')
        body.append(text(x + card_w / 2, top + 140, code, 11, t["text"], family=MONO, anchor="middle"))
        # The step in progress: an outline in its color, one step after another (2 s each, 8 s loop).
        body.append(f'<rect class="focus" style="animation-delay:{i * STEP}s" x="{x}" y="{top}" width="{card_w}" '
                    f'height="168" rx="12" fill="none" stroke="{color}" stroke-width="2"/>')
        if i < len(steps) - 1:
            body.append(arrow(x + card_w + 6, top + 84, x + card_w + gap - 6, top + 84, t["arrow"]))
            # The JSON travelling to the next step, just before it lights up.
            body.append(f'<circle class="packet" style="animation-delay:{(i + 1) * STEP - 0.45}s" '
                        f'cx="{x + card_w + 7}" cy="{top + 84}" r="3.5" fill="{t[steps[i + 1][0]]}"/>')
    body.append(text(width / 2, top + 196, "Options JSON in, response JSON out: PassKi passes both through "
                     "unchanged, and your server stays in charge.", 12, t["muted"], anchor="middle"))
    loop = 4 * STEP
    style = (
        f".focus{{opacity:0;animation:focus {loop}s ease-in-out infinite}}"
        f"@keyframes focus{{0%{{opacity:0}}5%{{opacity:1}}20%{{opacity:1}}25%{{opacity:0}}100%{{opacity:0}}}}"
        f".packet{{opacity:0;animation:packet {loop}s ease-in-out infinite}}"
        f"@keyframes packet{{0%{{opacity:0;transform:translateX(0)}}1.5%{{opacity:1}}"
        f"5%{{opacity:1;transform:translateX({gap - 14}px)}}6.5%{{opacity:0;transform:translateX({gap - 14}px)}}"
        f"100%{{opacity:0;transform:translateX({gap - 14}px)}}}}"
    )
    return svg(width, top + 210, "".join(body), "How a passkey sign-in works with PassKi: your server creates "
               "options, PassKi shows the native sheet, the user confirms, your server verifies the response.",
               style)


PHASE = 5  # seconds each stage of a journey diagram stays highlighted
ROW = 0.9  # seconds between the "underneath" rows lighting up within a stage
PANEL_W, GAP, TOP, PANEL_H = 284, 24, 78, 496
SCREEN_H = 214


def rect(x, y, w, h, fill, rx=4, extra=""):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" {extra}/>'


def appear(name, start, loop):
    """CSS for an element that appears `start` seconds into the loop and stays until it restarts. With motion off
    it's simply visible."""
    at = start / loop * 100
    return (f".{name}{{animation:{name} {loop}s linear infinite}}"
            f"@keyframes {name}{{0%,{max(at - 0.1, 0):.2f}%{{opacity:0}}{at:.2f}%,97%{{opacity:1}}100%{{opacity:0}}}}")


def journey_frame(t, stages, draw_screen, aria):
    """Three stages along a timeline. Each has a screen (drawn by `draw_screen(i, cx, top)`, which returns SVG and
    CSS) and an "underneath" list of who does what, which lights up row by row while its stage plays."""
    width = 3 * PANEL_W + 2 * GAP
    loop = 3 * PHASE
    body, css = [], []
    first, last = PANEL_W / 2, width - PANEL_W / 2
    body.append(f'<line x1="{first}" y1="22" x2="{last}" y2="22" stroke="{t["stroke"]}" stroke-width="2"/>')
    body.append(f'<circle class="traveller" cx="{first}" cy="22" r="4" fill="{t["passki"]}"/>')
    for i, (kind, title, rows) in enumerate(stages):
        x0 = i * (PANEL_W + GAP)
        cx = x0 + PANEL_W / 2
        color = t[kind]
        body.append(f'<circle cx="{cx}" cy="22" r="12" fill="{color}"/>')
        body.append(text(cx, 26.5, str(i + 1), 12, "#ffffff", "700", anchor="middle"))
        body.append(text(cx, 56, title, 15, t["text"], "600", anchor="middle"))
        body.append(rect(x0, TOP - 8, PANEL_W, PANEL_H, t["card"], 14, f'stroke="{t["stroke"]}"'))
        body.append(f'<rect class="stage" style="animation-delay:{i * PHASE}s" x="{x0}" y="{TOP - 8}" '
                    f'width="{PANEL_W}" height="{PANEL_H}" rx="14" fill="none" stroke="{color}" stroke-width="2"/>')
        screen, screen_css = draw_screen(i, cx, TOP + 6)
        body.append(screen)
        css.append(screen_css)
        # Underneath: who does what, in order, joined by a thin line.
        rx_, ry = x0 + 26, TOP + 264
        body.append(f'<line x1="{x0 + 20}" y1="{TOP + 236}" x2="{x0 + PANEL_W - 20}" y2="{TOP + 236}" '
                    f'stroke="{t["stroke"]}"/>')
        body.append(text(x0 + 20, TOP + 252, "UNDERNEATH", 9.5, t["muted"], "700"))
        body.append(f'<line x1="{rx_}" y1="{ry}" x2="{rx_}" y2="{ry + (len(rows) - 1) * 54}" stroke="{t["stroke"]}" '
                    f'stroke-width="2"/>')
        for r, (who, label, row_lines) in enumerate(rows):
            y = ry + r * 54
            row = [f'<circle cx="{rx_}" cy="{y}" r="5" fill="{t[who]}" stroke="{t["card"]}" stroke-width="2"/>',
                   text(rx_ + 14, y + 3.5, label, 9.5, t[who], "700")]
            for n, line in enumerate(row_lines):
                code = line.startswith("`")
                row.append(text(rx_ + 14, y + 20 + n * 15, line.strip("`"), 11 if code else 11.5,
                                t["text"], family=MONO if code else SANS))
            delay = i * PHASE + 0.4 + r * ROW
            body.append(f'<g class="row{r}" style="animation-delay:{delay:.2f}s">{"".join(row)}</g>')
    # A row lights up in its turn and stays lit until its stage ends; at rest (no motion) every row is lit.
    for r in range(4):
        hold = (PHASE - 0.4 - r * ROW) / loop * 100
        css.append(f".row{r}{{animation:row{r} {loop}s ease-out infinite backwards}}"
                   f"@keyframes row{r}{{0%{{opacity:.5}}3%{{opacity:1}}{hold:.1f}%{{opacity:1}}"
                   f"{hold + 3:.1f}%{{opacity:.5}}100%{{opacity:.5}}}}")
    css.append(
        f".stage{{opacity:0;animation:stage {loop}s ease-in-out infinite}}"
        f"@keyframes stage{{0%{{opacity:0}}4%{{opacity:1}}30%{{opacity:1}}34%{{opacity:0}}100%{{opacity:0}}}}"
        f".traveller{{animation:traveller {loop}s ease-in-out infinite}}"
        f"@keyframes traveller{{0%,25%{{transform:translateX(0)}}33%,58%{{transform:translateX({PANEL_W + GAP}px)}}"
        f"66%,92%{{transform:translateX({2 * (PANEL_W + GAP)}px)}}100%{{transform:translateX(0)}}}}"
    )
    return svg(width, TOP + PANEL_H, "".join(body), aria, "".join(css))


def journey(t):
    """The user's journey: sign in as today, add a passkey, sign in with it next time. Phones show each stage."""
    phone_w = 140

    def button(x, y, w, label, fill, color, outline=None):
        stroke = f'stroke="{outline}"' if outline else ""
        return rect(x, y, w, 18, fill, 9, stroke) + text(x + w / 2, y + 12.5, label, 8.5, color, "600", anchor="middle")

    def fingerprint(cx, cy, color):
        arcs = "".join(f'<path d="M{cx - r} {cy + 2} a{r} {r} 0 0 1 {2 * r} 0" fill="none" stroke="{color}" '
                       f'stroke-width="1.6" stroke-linecap="round"/>' for r in (4, 8, 12))
        return f'<circle cx="{cx}" cy="{cy}" r="17" fill="{color}" opacity="0.12"/>' + arcs

    def key(x, y, color):
        return (f'<circle cx="{x}" cy="{y}" r="4.5" fill="none" stroke="{color}" stroke-width="2"/>'
                f'<path d="M{x + 4.5} {y} h9 v3 M{x + 10} {y} v3" fill="none" stroke="{color}" stroke-width="2" '
                f'stroke-linecap="round"/>')

    def sheet_for(i, cx, px, py, sx, sw, title):
        sheet = [rect(px + 5, py + 122, phone_w - 10, 100, t["sheet"], 14, f'stroke="{t["stroke"]}"'),
                 text(cx, py + 141, title, 9, t["text"], "700", anchor="middle"),
                 fingerprint(cx, py + 164, t["os"]),
                 button(sx + 8, py + 185, sw - 16, "Continue", t["os"], "#ffffff")]
        return f'<g class="sheet" style="animation-delay:{i * PHASE}s">{"".join(sheet)}</g>'

    def draw_screen(i, cx, py):
        px = cx - phone_w / 2
        sx, sw = px + 14, phone_w - 28
        frame = (f'<clipPath id="screen{i}"><rect x="{px + 5}" y="{py + 5}" width="{phone_w - 10}" '
                 f'height="{SCREEN_H - 10}" rx="14"/></clipPath>'
                 + rect(px, py, phone_w, SCREEN_H, t["screen"], 19, f'stroke="{t["stroke"]}" stroke-width="2"')
                 + rect(cx - 16, py + 9, 32, 6, t["stroke"], 3))
        if i == 0:
            screen = [text(sx, py + 42, "Sign in", 12, t["text"], "700"),
                      rect(sx, py + 54, sw, 16, t["field"]), rect(sx, py + 76, sw, 16, t["field"]),
                      button(sx, py + 102, sw, "Sign in", t["button"], t["on_button"]),
                      text(cx, py + 135, "or", 8, t["muted"], anchor="middle"),
                      button(sx, py + 144, sw, "Continue with Google", t["screen"], t["text"], t["stroke"])]
        elif i == 1:
            screen = [text(sx, py + 42, "Settings", 12, t["text"], "700"),
                      rect(sx, py + 54, sw, 14, t["field"]), rect(sx, py + 74, sw, 14, t["field"]),
                      rect(sx, py + 94, sw, 16, t["passki"], 4, 'opacity="0.18"'),
                      key(sx + 10, py + 102, t["passki"]),
                      text(sx + 30, py + 105.5, "Add a passkey", 8.5, t["text"], "600"),
                      sheet_for(i, cx, px, py, sx, sw, "Save a passkey?")]
        else:
            screen = [text(sx, py + 42, "Sign in", 12, t["text"], "700"),
                      button(sx, py + 56, sw, "Sign in with a passkey", t["passki"], "#ffffff"),
                      rect(sx, py + 84, sw, 16, t["field"]), rect(sx, py + 106, sw, 16, t["field"]),
                      sheet_for(i, cx, px, py, sx, sw, "Sign in as alice?")]
        css = ("" if i else
               f".sheet{{animation:sheet {3 * PHASE}s ease-out infinite backwards}}"
               f"@keyframes sheet{{0%{{transform:translateY(106px)}}8%{{transform:translateY(0)}}"
               f"100%{{transform:translateY(0)}}}}")
        return frame + f'<g clip-path="url(#screen{i})">{"".join(screen)}</g>', css

    # What happens underneath each stage: (who, label, lines); a line starting with "`" is code.
    stages = [
        ("server", "Sign in as today", [
            ("text", "YOUR APP", ["Sends the password, or the Google", "or Apple token, as it does today."]),
            ("server", "YOUR SERVER", ["Checks it and starts a session", "for alice. Nothing changes here."]),
            ("muted", "NO PASSKEY YET", ["Signed in, alice can now add one", "in Settings."]),
        ]),
        ("passki", "Add a passkey", [
            ("server", "YOUR SERVER", ["Sends creation options: a fresh", "challenge, alice's user handle, rp.id."]),
            ("passki", "PASSKI", ["`passkeys.register(options)`", "shows the system sheet."]),
            ("os", "THE DEVICE", ["Makes a new key pair. The private", "key stays in her password manager."]),
            ("server", "YOUR SERVER", ["Verifies the response, then stores", "the public key and credential ID."]),
        ]),
        ("os", "Next time: one tap", [
            ("server", "YOUR SERVER", ["Sends request options with a new", "challenge. No username needed."]),
            ("passki", "PASSKI", ["`passkeys.signIn(options)`", "shows alice's passkey."]),
            ("os", "THE DEVICE", ["Her fingerprint unlocks the private", "key, which signs the challenge."]),
            ("server", "YOUR SERVER", ["Checks the signature with the", "stored public key: she's signed in."]),
        ]),
    ]
    return journey_frame(t, stages, draw_screen,
                         "The user's journey with PassKi: 1. they sign in as today, with a password, Google or Apple; "
                         "2. signed in, they add a passkey in Settings (passkeys.register); 3. next time they sign in "
                         "with one tap (passkeys.signIn). Underneath: the device creates a key pair whose private key "
                         "never leaves it, your server stores the public key, and later checks the device's signature "
                         "of a fresh challenge with it.")


def setup_journey(t):
    """Setting up: generate the files, publish them, check everything. Developer screens show each stage."""
    win_w = 236
    loop = 3 * PHASE
    term_bg, term_text, term_ok, term_dim = t["term"], "#e6edf3", "#3fb950", "#8b949e"

    def window(i, cx, py, title, fill):
        x = cx - win_w / 2
        return (f'<clipPath id="win{i}"><rect x="{x}" y="{py}" width="{win_w}" height="{SCREEN_H}" rx="10"/></clipPath>'
                + rect(x, py, win_w, SCREEN_H, fill, 10, f'stroke="{t["stroke"]}" stroke-width="1.5"')
                + f'<path d="M{x} {py + 22} h{win_w}" stroke="{t["stroke"]}"/>'
                + "".join(f'<circle cx="{x + 12 + d * 10}" cy="{py + 11}" r="3.2" fill="{t["stroke"]}"/>'
                          for d in range(3))
                + text(cx, py + 15, title, 8.5, t["muted"], "600", anchor="middle"), x)

    def typed(i, x, y, lines, start, step=0.45, size=8.2):
        """Terminal-style lines that appear one after another from `start` seconds into the loop. The first line (the
        prompt or the address) is always there, so a screen is never empty."""
        out, css = [], []
        for n, (value, color, weight) in enumerate(lines):
            line = text(x, y + n * 14, value, size, color, weight, MONO)
            if n == 0:
                out.append(line)
                continue
            name = f"t{i}l{n}"
            out.append(f'<g class="{name}">{line}</g>')
            css.append(appear(name, start + n * step, loop))
        return "".join(out), "".join(css)

    def draw_screen(i, cx, py):
        if i == 0:
            frame, x = window(i, cx, py, "build.gradle.kts", t["screen"])
            code = [("plugins {", t["passki"]), ('    id("com.alirezaiyan.passki")', t["server"]), ("}", t["passki"]),
                    ("passki {", t["passki"]), ('    rpId = "example.com"', t["server"]), ("}", t["passki"])]
            body = "".join(text(x + 12, py + 38 + n * 12, v, 8.2, c, family=MONO) for n, (v, c) in enumerate(code))
            body += rect(x, py + 116, win_w, SCREEN_H - 116, term_bg, 0)
            lines, css = typed(i, x + 10, py + 132, [
                ("$ ./gradlew passkiSetup", term_text, "600"),
                ("✓ well-known/assetlinks.json", term_ok, "normal"),
                ("✓ well-known/apple-app-site-association", term_ok, "normal"),
                ("✓ iosApp entitlements: webcredentials", term_ok, "normal"),
                ("→ allow these origins on your server", term_dim, "normal"),
            ], start=0.5)
        elif i == 1:
            frame, x = window(i, cx, py, "example.com", t["screen"])
            body = (rect(x + 10, py + 30, win_w - 20, 16, t["field"], 8)
                    + text(x + 18, py + 41, "example.com/.well-known/assetlinks.json", 7.8, t["muted"], family=MONO))
            lines, css = typed(i, x + 14, py + 62, [
                ("[{", t["text"], "normal"),
                ('  "relation": ["delegate_permission/', t["text"], "normal"),
                ('    common.get_login_creds"],', t["text"], "normal"),
                ('  "target": { "namespace": "android_app",', t["text"], "normal"),
                ('    "package_name": "com.example.app",', t["text"], "normal"),
                ('    "sha256_cert_fingerprints":', t["text"], "normal"),
                ('      ["AB:CD:…:EF"] } }]', t["text"], "normal"),
            ], start=PHASE + 0.4, step=0.25, size=7.8)
            chip = rect(x + 14, py + 166, 128, 18, t["server"], 9, 'opacity="0.15"') + text(
                x + 78, py + 178.5, "200 · application/json", 8.5, t["server"], "700", anchor="middle")
            chip2 = text(x + 14, py + 202, "+ /.well-known/apple-app-site-association", 7.8, t["muted"], family=MONO)
            body += f'<g class="chip">{chip}{chip2}</g>'
            css += appear("chip", PHASE + 2.4, loop)
        else:
            frame, x = window(i, cx, py, "terminal", term_bg)
            lines, css = typed(i, x + 10, py + 40, [
                ("$ ./gradlew passkiCheck", term_text, "600"),
                ("✓ AND-01  assetlinks.json reachable", term_ok, "normal"),
                ("✓ AND-03  signing key listed", term_ok, "normal"),
                ("✓ AND-04  Google agrees", term_ok, "normal"),
                ("✓ IOS-01  AASA reachable", term_ok, "normal"),
                ("✓ IOS-02  app listed", term_ok, "normal"),
                ("✓ IOS-05  entitlement in the build", term_ok, "normal"),
                ("✓ SRV-02  server allows the origins", term_ok, "normal"),
                ("All checks passed", term_text, "700"),
            ], start=2 * PHASE + 0.4, step=0.35)
            body = ""
        return frame + f'<g clip-path="url(#win{i})">{body}{lines}</g>', css

    stages = [
        ("passki", "Generate the files", [
            ("text", "YOU", ["Pick the RP ID once; it can't change.", "`rpId = \"example.com\"`"]),
            ("passki", "PASSKISETUP", ["Reads your debug and release keys", "and the Xcode project's app ID."]),
            ("passki", "PASSKISETUP", ["Writes assetlinks.json, the AASA", "file and the iOS entitlement."]),
            ("muted", "PLAY APP SIGNING", ["Its key is in no keystore; add it:", "`extraFingerprints.add(\"AB:CD:…\")`"]),
        ]),
        ("domain", "Publish them", [
            ("domain", "YOUR DOMAIN", ["Serves both at /.well-known/: status", "200, no redirect, application/json."]),
            ("server", "YOUR SERVER", ["Uses the same RP ID and allows", "the origins passkiSetup printed."]),
            ("os", "GOOGLE · APPLE", ["Fetch and cache the files: minutes", "for Google, longer on Apple's CDN."]),
        ]),
        ("server", "Check everything", [
            ("passki", "PASSKICHECK", ["Fetches the live files and matches", "every fingerprint and app ID."]),
            ("passki", "PASSKICHECK", ["Checks the Xcode entitlement and", "your server; fails CI when wrong."]),
            ("os", "PASSKEYDOCTOR", ["Runs the same checks on a device,", "from a debug build."]),
            ("muted", "LATER", ["Any failure names its check,", "such as AND-03, and the fix."]),
        ]),
    ]
    return journey_frame(t, stages, draw_screen,
                         "Setting up PassKi: 1. generate the files with passkiSetup (or by hand), which reads your "
                         "debug and release signing keys and the Xcode app ID (add the Play App Signing key with "
                         "extraFingerprints) and writes assetlinks.json, the apple-app-site-association file and the iOS "
                         "entitlement; 2. publish them on your domain at /.well-known/ and allow the printed origins "
                         "on your server, while Google and Apple fetch the files; 3. check everything with "
                         "passkiCheck, in CI, and with PasskeyDoctor on a device.")


def setup(t):
    width, height = 900, 300

    def node(cx, cy, kind, title, sub):
        w, h = 230, 76
        x, y = cx - w / 2, cy - h / 2
        return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{t["card"]}" stroke="{t[kind]}" '
                f'stroke-width="2"/>' + text(cx, cy - 4, title, 16, t["text"], weight="600", anchor="middle")
                + text(cx, cy + 18, sub, 12, t["muted"], family=MONO, anchor="middle"))

    def edge(x1, y1, x2, y2, label, sub, lx, ly):
        return (f'<line class="link" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{t["arrow"]}" '
                f'stroke-width="2" stroke-dasharray="6 5"/>'
                + text(lx, ly, label, 12.5, t["text"], weight="600", anchor="middle")
                + text(lx, ly + 17, sub, 11, t["muted"], family=MONO, anchor="middle"))

    app, domain, server = (160, 230), (450, 62), (740, 230)
    body = [
        edge(app[0] + 40, app[1] - 38, domain[0] - 80, domain[1] + 38, "the domain lists the app",
             "assetlinks.json · AASA", 205, 128),
        edge(domain[0] + 80, domain[1] + 38, server[0] - 40, server[1] - 38, "same RP ID", "rpId = example.com",
             695, 128),
        edge(app[0] + 115, app[1], server[0] - 115, server[1], "the server allows the app",
             "origins: https:// · android:apk-key-hash:", 450, 201),
        node(*domain, "domain", "Your domain", "example.com"),
        node(*app, "passki", "Your app", "Passkeys()"),
        node(*server, "server", "Your server", "WebAuthn library"),
        text(width / 2, height - 14, "passkiSetup writes the files  ·  passkiCheck checks them in CI  ·  "
             "PasskeyDoctor checks from the device", 12, t["muted"], anchor="middle"),
    ]
    # The links drift slowly, like a connection that's live.
    style = ".link{animation:link 2.4s linear infinite}@keyframes link{to{stroke-dashoffset:-22}}"
    return svg(width, height, "".join(body), "Setup: your app, your domain and your server must trust each other. "
               "The domain's well-known files list the app, the server uses the domain as RP ID, and the server "
               "allows the app's origins.", style)


def main():
    for name, draw in (("journey", journey), ("setup-journey", setup_journey), ("how-it-works", how_it_works),
                       ("setup", setup)):
        for theme, colors in THEMES.items():
            path = os.path.join(HERE, f"{name}-{theme}.svg")
            with open(path, "w", encoding="utf-8") as f:
                f.write(draw(colors))
            print("wrote", os.path.relpath(path, os.path.dirname(os.path.dirname(HERE))))


if __name__ == "__main__":
    main()
