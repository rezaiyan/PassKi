import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { Marked, Renderer } from "marked";

const escapeHtml = (s: string) =>
  s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

const renderer = new Renderer();
// Docs use literal placeholders like <rpId>; never pass raw HTML through.
renderer.html = ({ text }) => escapeHtml(text);
const marked = new Marked({ renderer, gfm: true, async: false });

/** Rendered docs/checks/<id>.md (mirrored into content/checks at build time), or null. */
export function checkDocHtml(id: string): string | null {
  if (!/^[A-Z]{3}-\d{2}$/.test(id)) return null;
  const file = join(process.cwd(), "content", "checks", `${id}.md`);
  if (!existsSync(file)) return null;
  const md = readFileSync(file, "utf8")
    .replace(/^# .*\n+/, "") // the page renders its own title
    .replace(/^Platform: .*\n+/, "") // and platform/severity in its sidebar
    .replace(/\]\(([A-Z]{3}-\d{2})\.md\)/g, "](/checks/$1)");
  const html = marked.parse(md) as string;
  return html.replace(/<a href="(https?:)/g, '<a rel="noopener" href="$1');
}
