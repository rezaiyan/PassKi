import type { NextConfig } from "next";

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
      { source: "/.well-known/assetlinks.json", destination: "/api/well-known/assetlinks" },
      { source: "/.well-known/apple-app-site-association", destination: "/api/well-known/aasa" },
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
        ],
      },
    ];
  },
};

export default nextConfig;
