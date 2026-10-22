import { generate, zip } from "@/lib/generator";
import { errorResponse, json, limited, readJson } from "@/lib/http";
import { generatorInputFrom } from "@/lib/validate";

/** POST {rpId, packageName, fingerprints | android[], teamId, bundleId | ios[]} → files (JSON) or ?format=zip. */
export async function POST(req: Request) {
  try {
    const tooMany = limited(req, "generate", 30);
    if (tooMany) return tooMany;
    const input = generatorInputFrom(await readJson(req));
    const files = generate(input);
    if (new URL(req.url).searchParams.get("format") === "zip") {
      return new Response(Buffer.from(zip(files)), {
        headers: {
          "content-type": "application/zip",
          "content-disposition": `attachment; filename="passki-setup-${input.rpId}.zip"`,
          "cache-control": "no-store",
        },
      });
    }
    return json({ rpId: input.rpId, files });
  } catch (e) {
    return errorResponse(e);
  }
}
