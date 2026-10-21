import {
  createHash,
  generateKeyPairSync,
  randomBytes,
  sign,
  type KeyObject,
} from "node:crypto";
import { isoCBOR } from "@simplewebauthn/server/helpers";

/** Minimal ES256 software authenticator (like passki-test's FakeAuthenticator) producing `none` attestations. */
export class SoftAuthenticator {
  private readonly privateKey: KeyObject;
  private readonly cosePublicKey: Uint8Array;
  readonly credentialId = randomBytes(16);
  signCount = 0;
  userHandle = "";

  constructor(
    readonly origin: string,
    private readonly flags = { uv: true, be: true, bs: true },
  ) {
    const { privateKey, publicKey } = generateKeyPairSync("ec", {
      namedCurve: "P-256",
    });
    this.privateKey = privateKey;
    const jwk = publicKey.export({ format: "jwk" });
    const cose = new Map<number, number | Uint8Array>([
      [1, 2],
      [3, -7],
      [-1, 1],
      [-2, Buffer.from(jwk.x!, "base64url")],
      [-3, Buffer.from(jwk.y!, "base64url")],
    ]);
    this.cosePublicKey = isoCBOR.encode(cose);
  }

  private flagByte(attested: boolean): number {
    return (
      0x01 |
      (this.flags.uv ? 0x04 : 0) |
      (this.flags.be ? 0x08 : 0) |
      (this.flags.bs ? 0x10 : 0) |
      (attested ? 0x40 : 0)
    );
  }

  private authData(rpId: string, attested: boolean): Buffer {
    const counter = Buffer.alloc(4);
    counter.writeUInt32BE(this.signCount);
    const parts = [
      createHash("sha256").update(rpId).digest(),
      Buffer.from([this.flagByte(attested)]),
      counter,
    ];
    if (attested) {
      const len = Buffer.alloc(2);
      len.writeUInt16BE(this.credentialId.length);
      parts.push(
        Buffer.alloc(16),
        len,
        this.credentialId,
        Buffer.from(this.cosePublicKey),
      );
    }
    return Buffer.concat(parts);
  }

  private clientData(type: string, challenge: string): Buffer {
    return Buffer.from(
      JSON.stringify({
        type,
        challenge,
        origin: this.origin,
        crossOrigin: false,
      }),
    );
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  register(options: any) {
    this.userHandle = options.user.id;
    const authData = this.authData(options.rp.id, true);
    const attestationObject = isoCBOR.encode(
      new Map<string, unknown>([
        ["fmt", "none"],
        ["attStmt", new Map()],
        ["authData", authData],
      ]) as never,
    );
    const id = this.credentialId.toString("base64url");
    return {
      id,
      rawId: id,
      type: "public-key" as const,
      clientExtensionResults: {},
      response: {
        clientDataJSON: this.clientData(
          "webauthn.create",
          options.challenge,
        ).toString("base64url"),
        attestationObject: Buffer.from(attestationObject).toString("base64url"),
        transports: ["internal" as const, "hybrid" as const],
      },
    };
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  assert(options: any) {
    this.signCount++;
    const authData = this.authData(options.rpId, false);
    const clientData = this.clientData("webauthn.get", options.challenge);
    const signature = sign(
      "sha256",
      Buffer.concat([
        authData,
        createHash("sha256").update(clientData).digest(),
      ]),
      this.privateKey,
    );
    const id = this.credentialId.toString("base64url");
    return {
      id,
      rawId: id,
      type: "public-key" as const,
      clientExtensionResults: {},
      response: {
        clientDataJSON: clientData.toString("base64url"),
        authenticatorData: authData.toString("base64url"),
        signature: signature.toString("base64url"),
        userHandle: this.userHandle,
      },
    };
  }
}
