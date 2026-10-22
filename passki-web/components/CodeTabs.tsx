"use client";

import { useState } from "react";

export function CodeTabs({
  tabs,
}: {
  tabs: { label: string; file: string; content: React.ReactNode }[];
}) {
  const [active, setActive] = useState(0);
  return (
    <div className="codewin">
      <div className="codewin-bar" role="tablist" aria-label="Code samples">
        {tabs.map((t, i) => (
          <button
            key={t.label}
            type="button"
            role="tab"
            aria-selected={i === active}
            className="codewin-tab"
            onClick={() => setActive(i)}
          >
            {t.label}
          </button>
        ))}
        <span className="codewin-file">{tabs[active].file}</span>
      </div>
      {tabs.map((t, i) => (
        <div key={t.label} role="tabpanel" hidden={i !== active}>
          {t.content}
        </div>
      ))}
    </div>
  );
}
