// Mirrors the canonical check catalog (../docs/checks) into content/checks so the app never
// reads files outside passki-web at runtime. Outside the repo (e.g. a Docker build with
// passki-web as context) the committed mirror is used as is.
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, "..", "docs", "checks");
const target = join(root, "content", "checks");

if (!existsSync(join(source, "catalog.json"))) {
  console.log(`sync-catalog: ${source} not found, using the committed mirror in content/checks`);
  process.exit(0);
}
mkdirSync(target, { recursive: true });
for (const file of readdirSync(target)) {
  if (file.endsWith(".md")) rmSync(join(target, file));
}
const files = readdirSync(source).filter((f) => f === "catalog.json" || /^[A-Z]{3}-\d{2}\.md$/.test(f));
for (const file of files) copyFileSync(join(source, file), join(target, file));
console.log(`sync-catalog: copied ${files.length} file(s) from docs/checks`);
