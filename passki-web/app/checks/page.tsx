import type { Metadata } from "next";
import { catalog, groupByPlatform } from "@/lib/catalog";

export const metadata: Metadata = { title: "Checks" };

export default function ChecksPage() {
  const groups = groupByPlatform(catalog.checks);
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner">
          <h1>Setup checks</h1>
          <p className="lead">
            Stable IDs shared by <code>./gradlew passkiCheck</code>, the
            on-device <code>PasskeyDoctor</code> and the verifier. When a check
            fails anywhere, its ID leads here.
          </p>
          <nav className="jump" aria-label="Platforms">
            {groups.map((g) => (
              <a key={g.platform} href={`#${g.platform}`}>
                {g.label} <span>{g.checks.length}</span>
              </a>
            ))}
          </nav>
        </div>
      </header>
      <div className="container page-body">
        {groups.map((g) => (
          <section key={g.platform} id={g.platform} className="catalog-group">
            <h2>
              {g.label} <span>{g.checks.length} checks</span>
            </h2>
            <div className="panel">
              {g.checks.map((c) => (
                <a key={c.id} className="check-row" href={`/checks/${c.id}`}>
                  <span className="id-chip">{c.id}</span>
                  <span>
                    <span className="check-title">{c.title}</span>
                    <span className="check-fix">{c.fix}</span>
                  </span>
                  <span className={`tag ${c.severity}`}>{c.severity}</span>
                </a>
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}
