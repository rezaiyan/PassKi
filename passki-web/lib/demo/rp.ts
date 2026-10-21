import { randomBytes } from "node:crypto";
import {
  generateAuthenticationOptions,
  generateRegistrationOptions,
  verifyAuthenticationResponse,
  verifyRegistrationResponse,
  type AuthenticationResponseJSON,
  type AuthenticatorTransportFuture,
  type RegistrationResponseJSON,
} from "@simplewebauthn/server";
import { clock, DAY, MINUTE, purgeExpired, type Db } from "@/lib/db";
import { demoConfig, type DemoConfig } from "./config";

export const CHALLENGE_TTL = 5 * MINUTE;
export const USER_TTL = DAY;
export const FIXTURE_TTL = 7 * DAY;
export const FIXTURES_PER_PLATFORM = 20;
export const FIXTURE_PLATFORMS = ["android", "ios", "web", "other"] as const;

export class DemoError extends Error {
  constructor(
    message: string,
    readonly status = 400,
  ) {
    super(message);
  }
}

export interface Verdict {
  verified: boolean;
  credentialId: string;
  signCount: number;
  userVerified: boolean;
  backupEligible: boolean;
  backedUp: boolean;
  aaguid?: string;
  userHandle: string;
  origin: string;
  rpId: string;
}

function storeChallenge(
  db: Db,
  challenge: string,
  kind: "reg" | "auth",
  userId: string | null,
) {
  const now = clock.now();
  db.prepare(
    "INSERT INTO challenges (challenge, kind, user_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
  ).run(challenge, kind, userId, now, now + CHALLENGE_TTL);
}

/** Removes and returns the challenge (single use); null if unknown, expired or of the other kind. */
function takeChallenge(
  db: Db,
  challenge: string,
  kind: "reg" | "auth",
): { user_id: string | null } | null {
  purgeExpired(db, "challenges");
  const row = db
    .prepare(
      "DELETE FROM challenges WHERE challenge = ? AND kind = ? AND expires_at > ? RETURNING user_id",
    )
    .get(challenge, kind, clock.now()) as
    | { user_id: string | null }
    | undefined;
  return row ?? null;
}

/** The challenge a client signed, read from its clientDataJSON (verification re-checks it). */
function challengeOf(clientDataJSON: unknown): string {
  if (typeof clientDataJSON !== "string")
    throw new DemoError("response.clientDataJSON is missing");
  try {
    const parsed = JSON.parse(
      Buffer.from(clientDataJSON, "base64url").toString("utf8"),
    );
    if (typeof parsed.challenge === "string") return parsed.challenge;
  } catch {
    /* fallthrough */
  }
  throw new DemoError("response.clientDataJSON is not valid");
}

const sanitizeName = (value: unknown) =>
  typeof value === "string" && value.trim()
    ? value
        .trim()
        .slice(0, 64)
        .replace(/[^\w.@+ -]/g, "") || "demo"
    : `demo-${randomBytes(3).toString("hex")}`;

/** Creates an anonymous demo user (24 h TTL) and registration options. `user.id` is the handle the client keeps. */
export async function registrationOptions(
  db: Db,
  body: { username?: unknown },
  cfg: DemoConfig = demoConfig(),
) {
  purgeExpired(db, "demo_users");
  const userId = randomBytes(16);
  const handle = userId.toString("base64url");
  const name = sanitizeName(body.username);
  const now = clock.now();
  db.prepare(
    "INSERT INTO demo_users (id, name, created_at, expires_at) VALUES (?, ?, ?, ?)",
  ).run(handle, name, now, now + USER_TTL);
  const options = await generateRegistrationOptions({
    rpName: cfg.rpName,
    rpID: cfg.rpId,
    userName: name,
    userDisplayName: name,
    userID: userId,
    attestationType: "none",
    authenticatorSelection: {
      residentKey: "required",
      userVerification: "preferred",
    },
    supportedAlgorithmIDs: [-7, -257],
    timeout: CHALLENGE_TTL,
  });
  storeChallenge(db, options.challenge, "reg", handle);
  return options;
}

export async function verifyRegistration(
  db: Db,
  response: RegistrationResponseJSON,
  cfg: DemoConfig = demoConfig(),
): Promise<Verdict> {
  const challenge = challengeOf(response?.response?.clientDataJSON);
  const stored = takeChallenge(db, challenge, "reg");
  if (!stored?.user_id)
    throw new DemoError("unknown, expired or already used challenge");
  const user = db
    .prepare("SELECT id FROM demo_users WHERE id = ? AND expires_at > ?")
    .get(stored.user_id, clock.now()) as { id: string } | undefined;
  if (!user) throw new DemoError("demo user expired");
  let result;
  try {
    result = await verifyRegistrationResponse({
      response,
      expectedChallenge: challenge,
      expectedOrigin: cfg.origins,
      expectedRPID: cfg.rpId,
      requireUserVerification: false,
    });
  } catch (e) {
    throw new DemoError(`registration rejected: ${(e as Error).message}`);
  }
  if (!result.verified) throw new DemoError("registration not verified");
  const info = result.registrationInfo;
  const now = clock.now();
  db.prepare(
    `INSERT INTO credentials (id, user_id, public_key, counter, transports, aaguid, backup_eligible, backed_up, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    info.credential.id,
    user.id,
    Buffer.from(info.credential.publicKey),
    info.credential.counter,
    JSON.stringify(info.credential.transports ?? []),
    info.aaguid,
    info.credentialDeviceType === "multiDevice" ? 1 : 0,
    info.credentialBackedUp ? 1 : 0,
    now,
    now + USER_TTL,
  );
  return {
    verified: true,
    credentialId: info.credential.id,
    signCount: info.credential.counter,
    userVerified: info.userVerified,
    backupEligible: info.credentialDeviceType === "multiDevice",
    backedUp: info.credentialBackedUp,
    aaguid:
      info.aaguid === "00000000-0000-0000-0000-000000000000"
        ? undefined
        : info.aaguid,
    userHandle: user.id,
    origin: info.origin,
    rpId: cfg.rpId,
  };
}

/** Login options; with a `userHandle`, `allowCredentials` lists that user's credentials, otherwise discoverable. */
export async function authenticationOptions(
  db: Db,
  body: { userHandle?: unknown },
  cfg: DemoConfig = demoConfig(),
) {
  purgeExpired(db, "credentials");
  let allow:
    | { id: string; transports?: AuthenticatorTransportFuture[] }[]
    | undefined;
  let userId: string | null = null;
  if (typeof body.userHandle === "string" && body.userHandle) {
    const rows = db
      .prepare(
        "SELECT id, transports FROM credentials WHERE user_id = ? AND expires_at > ?",
      )
      .all(body.userHandle, clock.now()) as {
      id: string;
      transports: string | null;
    }[];
    if (rows.length === 0)
      throw new DemoError("unknown or expired demo user", 404);
    allow = rows.map((r) => ({
      id: r.id,
      transports: JSON.parse(r.transports ?? "[]"),
    }));
    userId = body.userHandle;
  }
  const options = await generateAuthenticationOptions({
    rpID: cfg.rpId,
    allowCredentials: allow,
    userVerification: "preferred",
    timeout: CHALLENGE_TTL,
  });
  storeChallenge(db, options.challenge, "auth", userId);
  return options;
}

export async function verifyAuthentication(
  db: Db,
  response: AuthenticationResponseJSON,
  cfg: DemoConfig = demoConfig(),
): Promise<Verdict> {
  const challenge = challengeOf(response?.response?.clientDataJSON);
  const stored = takeChallenge(db, challenge, "auth");
  if (!stored)
    throw new DemoError("unknown, expired or already used challenge");
  purgeExpired(db, "credentials");
  const cred = db
    .prepare("SELECT * FROM credentials WHERE id = ? AND expires_at > ?")
    .get(String(response.id), clock.now()) as
    | {
        id: string;
        user_id: string;
        public_key: Buffer;
        counter: number;
        transports: string | null;
        aaguid: string | null;
        backup_eligible: number;
      }
    | undefined;
  if (!cred) throw new DemoError("unknown or expired credential", 404);
  if (stored.user_id && stored.user_id !== cred.user_id)
    throw new DemoError("credential does not belong to this user");
  if (
    response.response.userHandle &&
    response.response.userHandle !== cred.user_id
  )
    throw new DemoError("userHandle does not match the credential");
  let result;
  try {
    result = await verifyAuthenticationResponse({
      response,
      expectedChallenge: challenge,
      expectedOrigin: cfg.origins,
      expectedRPID: cfg.rpId,
      requireUserVerification: false,
      credential: {
        id: cred.id,
        publicKey: new Uint8Array(cred.public_key),
        counter: cred.counter,
        transports: JSON.parse(cred.transports ?? "[]"),
      },
    });
  } catch (e) {
    throw new DemoError(`authentication rejected: ${(e as Error).message}`);
  }
  if (!result.verified) throw new DemoError("authentication not verified");
  const info = result.authenticationInfo;
  db.prepare(
    "UPDATE credentials SET counter = ?, backed_up = ? WHERE id = ?",
  ).run(info.newCounter, info.credentialBackedUp ? 1 : 0, cred.id);
  return {
    verified: true,
    credentialId: cred.id,
    signCount: info.newCounter,
    userVerified: info.userVerified,
    backupEligible: info.credentialDeviceType === "multiDevice",
    backedUp: info.credentialBackedUp,
    aaguid:
      cred.aaguid && cred.aaguid !== "00000000-0000-0000-0000-000000000000"
        ? cred.aaguid
        : undefined,
    userHandle: cred.user_id,
    origin: info.origin,
    rpId: cfg.rpId,
  };
}

/** Opt-in capture of a raw registration response for PassKi contract tests; keeps the last N per platform for 7 days. */
export function captureFixture(
  db: Db,
  platform: string,
  payload: unknown,
): void {
  if (!(FIXTURE_PLATFORMS as readonly string[]).includes(platform))
    throw new DemoError(
      `platform must be one of ${FIXTURE_PLATFORMS.join(", ")}`,
    );
  const json = JSON.stringify(payload);
  if (json.length > 64 * 1024) throw new DemoError("fixture too large");
  purgeExpired(db, "fixtures");
  const now = clock.now();
  db.prepare(
    "INSERT INTO fixtures (platform, json, created_at, expires_at) VALUES (?, ?, ?, ?)",
  ).run(platform, json, now, now + FIXTURE_TTL);
  db.prepare(
    "DELETE FROM fixtures WHERE platform = ? AND id NOT IN (SELECT id FROM fixtures WHERE platform = ? ORDER BY id DESC LIMIT ?)",
  ).run(platform, platform, FIXTURES_PER_PLATFORM);
}

export function listFixtures(db: Db, platform?: string) {
  purgeExpired(db, "fixtures");
  const rows = (
    platform
      ? db
          .prepare(
            "SELECT id, platform, json, created_at FROM fixtures WHERE platform = ? ORDER BY id DESC",
          )
          .all(platform)
      : db
          .prepare(
            "SELECT id, platform, json, created_at FROM fixtures ORDER BY id DESC",
          )
          .all()
  ) as { id: number; platform: string; json: string; created_at: number }[];
  return rows.map((r) => ({
    id: r.id,
    platform: r.platform,
    capturedAt: new Date(r.created_at).toISOString(),
    ...JSON.parse(r.json),
  }));
}
