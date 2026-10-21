/** Startup cleanup: opening the DB purges expired rows (lazy purges run on every access afterwards). */
export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { getDb, purgeAll } = await import("@/lib/db");
    const removed = purgeAll(getDb());
    console.log(`passki-web: database ready, purged ${removed} expired row(s)`);
  }
}
