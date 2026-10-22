type IconProps = { size?: number };

const stroke = (size: number) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
});

export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--brand)" />
      <circle
        cx="12.5"
        cy="13"
        r="5"
        fill="none"
        stroke="#fff"
        strokeWidth="2.6"
      />
      <path
        d="M16.2 16.6 24 24.4M21 21.4l2.4-2.4M19 19.4l1.8-1.8"
        stroke="#fff"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

export const CheckIcon = ({ size = 16 }: IconProps) => (
  <svg {...stroke(size)} strokeWidth={2.6}>
    <path d="M5 12.5 10 17l9-10" />
  </svg>
);
export const CrossIcon = ({ size = 16 }: IconProps) => (
  <svg {...stroke(size)} strokeWidth={2.6}>
    <path d="M7 7l10 10M17 7 7 17" />
  </svg>
);
export const WarnIcon = ({ size = 16 }: IconProps) => (
  <svg {...stroke(size)} strokeWidth={2.6}>
    <path d="M12 6v7M12 17.5v.5" />
  </svg>
);
export const SkipIcon = ({ size = 16 }: IconProps) => (
  <svg {...stroke(size)} strokeWidth={2.6}>
    <path d="M7 12h10" />
  </svg>
);
export const FilesIcon = ({ size = 20 }: IconProps) => (
  <svg {...stroke(size)}>
    <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
    <path d="M14 3v5h5M9 13h6M9 17h4" />
  </svg>
);
export const ShieldIcon = ({ size = 20 }: IconProps) => (
  <svg {...stroke(size)}>
    <path d="M12 3 5 6v6c0 4.4 3 7.7 7 9 4-1.3 7-4.6 7-9V6z" />
    <path d="m9 12 2 2 4-4" />
  </svg>
);
export const ServerIcon = ({ size = 20 }: IconProps) => (
  <svg {...stroke(size)}>
    <rect x="4" y="4" width="16" height="7" rx="2" />
    <rect x="4" y="13" width="16" height="7" rx="2" />
    <path d="M8 7.5h.01M8 16.5h.01" />
  </svg>
);
export const CopyIcon = ({ size = 14 }: IconProps) => (
  <svg {...stroke(size)}>
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V6a2 2 0 0 1 2-2h9" />
  </svg>
);
export const DownloadIcon = ({ size = 16 }: IconProps) => (
  <svg {...stroke(size)}>
    <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
  </svg>
);
export const GitHubIcon = ({ size = 16 }: IconProps) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="currentColor"
    aria-hidden="true"
  >
    <path d="M12 2a10 10 0 0 0-3.2 19.5c.5.1.7-.2.7-.5v-1.7c-2.8.6-3.4-1.3-3.4-1.3-.5-1.2-1.1-1.5-1.1-1.5-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.6 2.4 1.1 3 .9.1-.7.4-1.1.6-1.4-2.2-.2-4.6-1.1-4.6-5 0-1.1.4-2 1-2.7-.1-.3-.4-1.3.1-2.7 0 0 .8-.3 2.8 1a9.6 9.6 0 0 1 5 0c1.9-1.3 2.8-1 2.8-1 .5 1.4.2 2.4.1 2.7.6.7 1 1.6 1 2.7 0 3.9-2.4 4.8-4.6 5 .4.3.7.9.7 1.9V21c0 .3.2.6.7.5A10 10 0 0 0 12 2" />
  </svg>
);

export function StatusIcon({
  status,
}: {
  status: "pass" | "warn" | "fail" | "skip";
}) {
  const label = {
    pass: "Passed",
    warn: "Warning",
    fail: "Failed",
    skip: "Skipped",
  }[status];
  return (
    <span className="status-icon" role="img" aria-label={label}>
      {status === "pass" ? (
        <CheckIcon />
      ) : status === "fail" ? (
        <CrossIcon />
      ) : status === "warn" ? (
        <WarnIcon />
      ) : (
        <SkipIcon />
      )}
    </span>
  );
}
