/** Tiny Kotlin highlighter for static marketing snippets: comments, strings, keywords, calls, types. */

const TOKEN =
  /(\/\/[^\n]*)|("(?:[^"\\]|\\.)*")|(@\w+)|\b(val|var|fun|when|is|else|if|return|null|true|false|object|class|suspend|import|by)\b|\b([A-Z]\w*)\b|\b([a-z]\w*)(?=\s*[({])|\b(\d+)\b/g;
const KIND = ["com", "str", "ann", "kw", "type", "fn", "num"];

export function Kotlin({ code }: { code: string }) {
  const parts: React.ReactNode[] = [];
  let last = 0;
  for (const m of code.matchAll(TOKEN)) {
    if (m.index > last) parts.push(code.slice(last, m.index));
    const group = m.slice(1).findIndex((g) => g !== undefined);
    parts.push(
      <span key={m.index} className={`k-${KIND[group]}`}>
        {m[0]}
      </span>,
    );
    last = m.index + m[0].length;
  }
  parts.push(code.slice(last));
  return (
    <pre className="kotlin">
      <code>{parts}</code>
    </pre>
  );
}
