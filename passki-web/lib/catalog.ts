import catalogJson from "@/content/checks/catalog.json";

export type Platform = "android" | "ios" | "server" | "config" | "device" | (string & {});
export type Severity = "error" | "warning";

export interface CatalogCheck {
  id: string;
  platform: Platform;
  severity: Severity;
  title: string;
  fix: string;
}

export interface Catalog {
  version: number;
  docsBase: string;
  docsRepo?: string;
  checks: CatalogCheck[];
}

export const catalog: Catalog = catalogJson as Catalog;

const byId = new Map(catalog.checks.map((c) => [c.id, c]));

/** The catalog entry for [id]; throws for unknown IDs so typos fail fast in tests. */
export function check(id: string): CatalogCheck {
  const entry = byId.get(id);
  if (!entry) throw new Error(`Unknown check ID ${id}`);
  return entry;
}

export function isCheckId(id: string): boolean {
  return byId.has(id);
}

export type FixParams = Partial<
  Record<
    | "rpId"
    | "package"
    | "fingerprint"
    | "teamId"
    | "bundleId"
    | "path"
    | "hash",
    string
  >
>;

/**
 * The catalog fix text for [id] with `<placeholder>`s filled from [params].
 * Unfilled placeholders stay visible so the reader knows what to substitute.
 */
export function fixText(id: string, params: FixParams = {}): string {
  return check(id).fix.replace(
    /<(rpId|package|fingerprint|teamId|bundleId|path|hash)>/g,
    (whole, key: keyof FixParams) => {
      const value = params[key];
      return value === undefined || value === "" ? whole : value;
    },
  );
}

/** Docs link on this site (relative), e.g. `/checks/AND-01`. */
export function docsPath(id: string): string {
  return `/checks/${id}`;
}

const PLATFORM_LABELS: Record<string, string> = {
  android: "Android",
  ios: "iOS",
  server: "Server",
  config: "Config",
  device: "Device",
};

const PLATFORM_ORDER = Object.keys(PLATFORM_LABELS);

/** Human label for a platform; unknown (future) values fall back to a capitalised raw value, never throw. */
export function platformLabel(platform: string | undefined | null): string {
  if (!platform) return "Other";
  const known = Object.hasOwn(PLATFORM_LABELS, platform) ? PLATFORM_LABELS[platform] : undefined;
  return known ?? platform.charAt(0).toUpperCase() + platform.slice(1);
}

/** Checks grouped by platform: known platforms in a fixed order, unknown ones after, alphabetically. */
export function groupByPlatform(
  checks: readonly CatalogCheck[],
): { platform: string; label: string; checks: CatalogCheck[] }[] {
  const groups = new Map<string, CatalogCheck[]>();
  for (const c of checks) {
    const key = c.platform || "other";
    groups.set(key, [...(groups.get(key) ?? []), c]);
  }
  const rank = (p: string) => {
    const i = PLATFORM_ORDER.indexOf(p);
    return i === -1 ? PLATFORM_ORDER.length : i;
  };
  return [...groups.keys()]
    .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
    .map((platform) => ({ platform, label: platformLabel(platform), checks: groups.get(platform)! }));
}

/** "Edit on GitHub" URL for a check page; null unless the catalog sets docsRepo. */
export function editUrl(id: string, repo: string | undefined): string | null {
  return repo ? `${repo}${id}.md` : null;
}
