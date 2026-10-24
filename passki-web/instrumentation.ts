/**
 * Startup checks: refuse to serve without an IP salt in production, then open the DB, which purges expired rows
 * (lazy purges run on every access afterwards).
 */
export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { getDb, ipSalt, purgeAll } = await import("@/lib/db");
    try {
      ipSalt();
    } catch (e) {
      console.error(`passki-web: ${(e as Error).message}`);
      process.exit(1);
    }
    const removed = purgeAll(getDb());
    console.log(`passki-web: database ready, purged ${removed} expired row(s)`);
  }
}
