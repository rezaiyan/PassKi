import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  catalog,
  isCheckId,
  check,
  editUrl,
  platformLabel,
} from "@/lib/catalog";
import { checkDocHtml } from "@/lib/docs";
import { GitHubIcon } from "@/components/Icons";

export const dynamicParams = false;

export function generateStaticParams() {
  return catalog.checks.map((c) => ({ id: c.id }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  return isCheckId(id) ? { title: `${id} ${check(id).title}` } : {};
}

export default async function CheckPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!isCheckId(id)) notFound();
  const c = check(id);
  const html = checkDocHtml(id);
  const edit = editUrl(c.id, catalog.docsRepo);
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner">
          <p className="crumb">
            <Link href="/checks">All checks</Link>
          </p>
          <h1 className="doc-title">
            <span className="id-chip">{c.id}</span> {c.title}
          </h1>
        </div>
      </header>
      <div className="container page-body">
        <div className="doc-layout">
          <div className="panel panel-body">
            {html ? (
              <article
                className="doc"
                dangerouslySetInnerHTML={{ __html: html }}
              />
            ) : (
              <p className="doc">
                <strong>Fix:</strong> {c.fix}
              </p>
            )}
          </div>
          <aside className="panel panel-body">
            <dl className="meta-list">
              <div>
                <dt>Platform</dt>
                <dd>{platformLabel(c.platform)}</dd>
              </div>
              <div>
                <dt>Severity</dt>
                <dd>
                  <span className={`tag ${c.severity}`}>{c.severity}</span>
                </dd>
              </div>
              <div>
                <dt>Reported by</dt>
                <dd>
                  Gradle <code>passkiCheck</code>, <code>PasskeyDoctor</code>{" "}
                  and the verifier
                </dd>
              </div>
              {edit && (
                <div>
                  <dt>Improve this page</dt>
                  <dd>
                    <a
                      href={edit}
                      style={{
                        display: "inline-flex",
                        gap: 6,
                        alignItems: "center",
                      }}
                    >
                      <GitHubIcon size={14} /> Edit on GitHub
                    </a>
                  </dd>
                </div>
              )}
            </dl>
          </aside>
        </div>
      </div>
    </>
  );
}
