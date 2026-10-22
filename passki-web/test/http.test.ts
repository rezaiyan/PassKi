import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { openDb, setDb } from "@/lib/db";
import { readJson } from "@/lib/http";
import { POST as generatePost } from "@/app/api/generate/route";
import { POST as regVerify } from "@/app/api/demo/register/verify/route";
import { POST as loginVerify } from "@/app/api/demo/login/verify/route";

function streamReq(chunks: string[], headers: Record<string, string> = {}, pulled = { n: 0 }) {
  const enc = new TextEncoder();
  let i = 0;
  const body = new ReadableStream<Uint8Array>({
    pull(c) {
      if (i < chunks.length) {
        pulled.n++;
        c.enqueue(enc.encode(chunks[i++]));
      } else c.close();
    },
  });
  return new Request("http://x/", { method: "POST", body, headers, duplex: "half" } as RequestInit);
}

describe("readJson", () => {
  it("should parse a small body", async () => {
    expect(await readJson(streamReq(['{"a":', "1}"]))).toEqual({ a: 1 });
  });
  it("should reject with 413 and stop reading once the cap is exceeded", async () => {
    const pulled = { n: 0 };
    const chunks = Array.from({ length: 1000 }, () => "x".repeat(1024));
    await expect(readJson(streamReq(chunks, {}, pulled), 4096)).rejects.toMatchObject({ status: 413 });
    expect(pulled.n).toBeLessThan(20);
  });
  it("should reject on Content-Length over the cap without reading", async () => {
    const pulled = { n: 0 };
    await expect(readJson(streamReq(["{}"], { "content-length": "999999" }, pulled), 4096)).rejects.toMatchObject({ status: 413 });
  });
});

describe("rate limits", () => {
  beforeEach(() => setDb(openDb(":memory:")));
  afterEach(() => setDb(undefined));
  const mk = () => new Request("http://x/", { method: "POST", body: "{}", headers: { "x-forwarded-for": "9.9.9.9" } });

  it("should 429 /api/generate after 30 requests", async () => {
    for (let i = 0; i < 30; i++) expect((await generatePost(mk())).status).not.toBe(429);
    expect((await generatePost(mk())).status).toBe(429);
  });
  it("should 429 register/verify after 60 requests", async () => {
    for (let i = 0; i < 60; i++) expect((await regVerify(mk())).status).not.toBe(429);
    expect((await regVerify(mk())).status).toBe(429);
  });
  it("should 429 login/verify after 60 requests", async () => {
    for (let i = 0; i < 60; i++) expect((await loginVerify(mk())).status).not.toBe(429);
    expect((await loginVerify(mk())).status).toBe(429);
  });
});
