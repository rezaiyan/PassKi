import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { catalog, isCheckId, check } from "@/lib/catalog";
import { checkDocHtml } from "@/lib/docs";

export const dynamicParams = false;

export function generateStaticParams() {
  return catalog.checks.map((c) => ({ id: c.id }));
}

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params;
  return isCheckId(id) ? { title: `${id} ${check(id).title}` } : {};
}

export default async function CheckPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!isCheckId(id)) notFound();
  const c = check(id);
  const html = checkDocHtml(id);
  return (
    <>
      <p className="muted"><a href="/checks">All checks</a></p>
      <h1>{c.id}: {c.title}</h1>
      <p className="lead">Platform: {c.platform} · Severity: {c.severity}</p>
      {html ? (
        <article className="doc" dangerouslySetInnerHTML={{ __html: html }} />
      ) : (
        <div className="card"><strong>Fix:</strong> {c.fix}</div>
      )}
      {catalog.docsRepo && <p className="muted"><a href={`${catalog.docsRepo}${c.id}.md`}>Edit this page on GitHub</a></p>}
    </>
  );
}
