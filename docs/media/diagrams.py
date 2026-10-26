#!/usr/bin/env python3
"""Draws the README's diagrams as SVG, in a light and a dark version (the README picks one with <picture>).

Usage: python3 docs/media/diagrams.py   (writes docs/media/how-it-works-*.svg and docs/media/setup-*.svg)
"""
import os
from html import escape

HERE = os.path.dirname(os.path.abspath(__file__))
SANS = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

STEP = 2  # seconds each step of the sign-in diagram stays highlighted

THEMES = {
    "light": dict(card="#f6f8fa", stroke="#d0d7de", text="#1f2328", muted="#59636e", arrow="#8c959f",
                  server="#1a7f37", passki="#7f52ff", os="#0969da", domain="#bc4c00", code="#eaeef2"),
    "dark": dict(card="#161b22", stroke="#30363d", text="#e6edf3", muted="#9198a1", arrow="#6e7681",
                 server="#3fb950", passki="#a78bfa", os="#4493f8", domain="#f0883e", code="#21262d"),
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
    for name, draw in (("how-it-works", how_it_works), ("setup", setup)):
        for theme, colors in THEMES.items():
            path = os.path.join(HERE, f"{name}-{theme}.svg")
            with open(path, "w", encoding="utf-8") as f:
                f.write(draw(colors))
            print("wrote", os.path.relpath(path, os.path.dirname(os.path.dirname(HERE))))


if __name__ == "__main__":
    main()
