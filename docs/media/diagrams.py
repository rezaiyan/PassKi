#!/usr/bin/env python3
"""Draws the README's diagrams as SVG, in a light and a dark version (the README picks one with <picture>).

Usage: python3 docs/media/diagrams.py   (writes docs/media/{journey,how-it-works,setup}-{light,dark}.svg)
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
                  screen="#ffffff", field="#eaeef2", sheet="#ffffff", button="#1f2328", on_button="#ffffff"),
    "dark": dict(card="#161b22", stroke="#30363d", text="#e6edf3", muted="#9198a1", arrow="#6e7681",
                 server="#3fb950", passki="#a78bfa", os="#4493f8", domain="#f0883e", code="#21262d",
                 screen="#0d1117", field="#21262d", sheet="#1c2128", button="#e6edf3", on_button="#0d1117"),
}


def text(x, y, value, size, fill, weight="normal", family=SANS, anchor="start"):
    return (f'<text x="{x}" y="{y}" font-family="{family}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}">{escape(value)}</text>')


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


PHASE = 5  # seconds each stage of the journey diagram stays highlighted
ROW = 0.9  # seconds between the "underneath" rows lighting up within a stage


def journey(t):
    """The user's journey: sign in as today, add a passkey, sign in with it next time. Phones show each stage."""
    panel_w, gap, phone_w, phone_h, top = 284, 24, 140, 214, 78
    width = 3 * panel_w + 2 * gap
    body = []

    def rect(x, y, w, h, fill, rx=4, extra=""):
        return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" {extra}/>'

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
    # Timeline across the top, with a dot moving along it.
    first, last = panel_w / 2, width - panel_w / 2
    body.append(f'<line x1="{first}" y1="22" x2="{last}" y2="22" stroke="{t["stroke"]}" stroke-width="2"/>')
    body.append(f'<circle class="traveller" cx="{first}" cy="22" r="4" fill="{t["passki"]}"/>')
    panel_h = 496
    for i, (kind, title, rows) in enumerate(stages):
        x0 = i * (panel_w + gap)
        cx = x0 + panel_w / 2
        color = t[kind]
        body.append(f'<circle cx="{cx}" cy="22" r="12" fill="{color}"/>')
        body.append(text(cx, 26.5, str(i + 1), 12, "#ffffff", "700", anchor="middle"))
        body.append(text(cx, 56, title, 15, t["text"], "600", anchor="middle"))
        body.append(rect(x0, top - 8, panel_w, panel_h, t["card"], 14, f'stroke="{t["stroke"]}"'))
        body.append(f'<rect class="stage" style="animation-delay:{i * PHASE}s" x="{x0}" y="{top - 8}" '
                    f'width="{panel_w}" height="{panel_h}" rx="14" fill="none" stroke="{color}" stroke-width="2"/>')
        # The phone and its screen.
        px, py = cx - phone_w / 2, top + 6
        body.append(f'<clipPath id="screen{i}"><rect x="{px + 5}" y="{py + 5}" width="{phone_w - 10}" '
                    f'height="{phone_h - 10}" rx="14"/></clipPath>')
        body.append(rect(px, py, phone_w, phone_h, t["screen"], 19, f'stroke="{t["stroke"]}" stroke-width="2"'))
        body.append(rect(cx - 16, py + 9, 32, 6, t["stroke"], 3))
        sx, sw = px + 14, phone_w - 28
        screen = []
        if i == 0:
            screen += [text(sx, py + 42, "Sign in", 12, t["text"], "700"),
                       rect(sx, py + 54, sw, 16, t["field"]), rect(sx, py + 76, sw, 16, t["field"]),
                       button(sx, py + 102, sw, "Sign in", t["button"], t["on_button"]),
                       text(cx, py + 135, "or", 8, t["muted"], anchor="middle"),
                       button(sx, py + 144, sw, "Continue with Google", t["screen"], t["text"], t["stroke"])]
        elif i == 1:
            screen += [text(sx, py + 42, "Settings", 12, t["text"], "700"),
                       rect(sx, py + 54, sw, 14, t["field"]), rect(sx, py + 74, sw, 14, t["field"]),
                       rect(sx, py + 94, sw, 16, t["passki"], 4, 'opacity="0.18"'),
                       key(sx + 10, py + 102, t["passki"]),
                       text(sx + 30, py + 105.5, "Add a passkey", 8.5, t["text"], "600")]
            sheet = [rect(px + 5, py + 122, phone_w - 10, 100, t["sheet"], 14, f'stroke="{t["stroke"]}"'),
                     text(cx, py + 141, "Save a passkey?", 9, t["text"], "700", anchor="middle"),
                     fingerprint(cx, py + 164, t["os"]),
                     button(sx + 8, py + 185, sw - 16, "Continue", t["os"], "#ffffff")]
            screen.append(f'<g class="sheet" style="animation-delay:{i * PHASE}s">{"".join(sheet)}</g>')
        else:
            screen += [text(sx, py + 42, "Sign in", 12, t["text"], "700"),
                       button(sx, py + 56, sw, "Sign in with a passkey", t["passki"], "#ffffff"),
                       rect(sx, py + 84, sw, 16, t["field"]), rect(sx, py + 106, sw, 16, t["field"])]
            sheet = [rect(px + 5, py + 122, phone_w - 10, 100, t["sheet"], 14, f'stroke="{t["stroke"]}"'),
                     text(cx, py + 141, "Sign in as alice?", 9, t["text"], "700", anchor="middle"),
                     fingerprint(cx, py + 164, t["os"]),
                     button(sx + 8, py + 185, sw - 16, "Continue", t["os"], "#ffffff")]
            screen.append(f'<g class="sheet" style="animation-delay:{i * PHASE}s">{"".join(sheet)}</g>')
        body.append(f'<g clip-path="url(#screen{i})">{"".join(screen)}</g>')
        # Underneath: who does what, in order, joined by a thin line.
        rx_, ry = x0 + 26, top + 264
        body.append(f'<line x1="{x0 + 20}" y1="{top + 236}" x2="{x0 + panel_w - 20}" y2="{top + 236}" '
                    f'stroke="{t["stroke"]}"/>')
        body.append(text(x0 + 20, top + 252, "UNDERNEATH", 9.5, t["muted"], "700"))
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
    loop = 3 * PHASE
    # A row lights up in its turn and stays lit until its stage ends; at rest (no motion) every row is lit.
    rows_css = ""
    for r in range(4):
        hold = (PHASE - 0.4 - r * ROW) / loop * 100
        rows_css += (f".row{r}{{animation:row{r} {loop}s ease-out infinite backwards}}"
                     f"@keyframes row{r}{{0%{{opacity:.5}}3%{{opacity:1}}{hold:.1f}%{{opacity:1}}"
                     f"{hold + 3:.1f}%{{opacity:.5}}100%{{opacity:.5}}}}")
    style = rows_css + (
        f".stage{{opacity:0;animation:stage {loop}s ease-in-out infinite}}"
        f"@keyframes stage{{0%{{opacity:0}}4%{{opacity:1}}30%{{opacity:1}}34%{{opacity:0}}100%{{opacity:0}}}}"
        # Each sheet rises when its stage starts and stays up; it rests up when motion is off.
        f".sheet{{animation:sheet {loop}s ease-out infinite backwards}}"
        f"@keyframes sheet{{0%{{transform:translateY(106px)}}8%{{transform:translateY(0)}}100%{{transform:translateY(0)}}}}"
        f".traveller{{animation:traveller {loop}s ease-in-out infinite}}"
        f"@keyframes traveller{{0%,25%{{transform:translateX(0)}}33%,58%{{transform:translateX({panel_w + gap}px)}}"
        f"66%,92%{{transform:translateX({2 * (panel_w + gap)}px)}}100%{{transform:translateX(0)}}}}"
    )
    return svg(width, top + panel_h, "".join(body), "The user's journey with PassKi: 1. they sign in as today, with a "
               "password, Google or Apple; 2. signed in, they add a passkey in Settings (passkeys.register); "
               "3. next time they sign in with one tap (passkeys.signIn). Underneath: the device creates a key pair whose "
               "private key never leaves it, your server stores the public key, and later checks the device's "
               "signature of a fresh challenge with it.", style)


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
    for name, draw in (("journey", journey), ("how-it-works", how_it_works), ("setup", setup)):
        for theme, colors in THEMES.items():
            path = os.path.join(HERE, f"{name}-{theme}.svg")
            with open(path, "w", encoding="utf-8") as f:
                f.write(draw(colors))
            print("wrote", os.path.relpath(path, os.path.dirname(os.path.dirname(HERE))))


if __name__ == "__main__":
    main()
