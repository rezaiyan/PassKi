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
