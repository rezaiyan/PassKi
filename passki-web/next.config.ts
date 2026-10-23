import type { NextConfig } from "next";

const dev = process.env.NODE_ENV === "development";

// Next's inline bootstrap scripts need 'unsafe-inline' without a nonce; dev also needs 'unsafe-eval' for HMR.
const CSP = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "media-src 'self'",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const PERMISSIONS = [
  "camera=()",
  "microphone=()",
  "geolocation=()",
  "payment=()",
  "usb=()",
  "publickey-credentials-create=(self)",
  "publickey-credentials-get=(self)",
].join(", ");

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  serverExternalPackages: ["better-sqlite3"],
  // The native addon is loaded through `bindings`, which file tracing can miss.
  outputFileTracingIncludes: {
    "/**": ["./node_modules/better-sqlite3/build/Release/better_sqlite3.node"],
  },
  async rewrites() {
    return [
      {
        source: "/.well-known/assetlinks.json",
        destination: "/api/well-known/assetlinks",
      },
      {
        source: "/.well-known/apple-app-site-association",
        destination: "/api/well-known/aasa",
      },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          {
            key: "Strict-Transport-Security",
            value: "max-age=31536000; includeSubDomains",
          },
          { key: "Content-Security-Policy", value: CSP },
          { key: "Permissions-Policy", value: PERMISSIONS },
        ],
      },
    ];
  },
};

export default nextConfig;
