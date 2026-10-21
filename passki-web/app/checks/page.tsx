import type { Metadata } from "next";
import { catalog, groupByPlatform, platformLabel } from "@/lib/catalog";

export const metadata: Metadata = { title: "Checks" };

export default function ChecksPage() {
  return (
    <>
      <h1>Setup checks</h1>
      <p className="lead">Stable IDs shared by <code>./gradlew passkiCheck</code>, the on-device <code>PasskeyDoctor</code> and this dashboard (catalog v{catalog.version}).</p>
      {groupByPlatform(catalog.checks).map((g) => (
        <section key={g.platform} id={g.platform}>
          <h2>{g.label}</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th scope="col">ID</th><th scope="col">Platform</th><th scope="col">Severity</th><th scope="col">Check</th></tr>
              </thead>
              <tbody>
                {g.checks.map((c) => (
                  <tr key={c.id}>
                    <td><a href={`/checks/${c.id}`}><strong>{c.id}</strong></a></td>
                    <td>{platformLabel(c.platform)}</td>
                    <td>{c.severity}</td>
                    <td>{c.title}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </>
  );
}
