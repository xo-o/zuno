#!/usr/bin/env node

/**
 * Automated Google Play Console uploader using Google Play Android Developer API v3.
 * Zero external npm dependencies (uses Node.js built-in crypto, fs, os, path, and fetch).
 *
 * Usage:
 *   node android/scripts/play-upload.mjs --check
 *   node android/scripts/play-upload.mjs --aab android/app/build/outputs/bundle/release/app-release.aab --track internal
 */

import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_PACKAGE = "com.zuno.ai";
const DEFAULT_TRACK = "internal";
const DEFAULT_STATUS = "completed";
const DEFAULT_CREDENTIALS = path.join(os.homedir(), ".gradle", "zuno-play-publisher-credentials.json");
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

function printHelp() {
  console.log(`Usage: play-upload.mjs [options]

Options:
  --aab <path>                  Path to signed .aab file (required unless --check or --version-code)
  --version-code <n>            Assign an already-uploaded versionCode to --track without uploading an .aab
  --package <name>              Android applicationId (default: ${DEFAULT_PACKAGE})
  --track <name>                Play track: internal | alpha | beta | production (default: ${DEFAULT_TRACK})
  --status <status>             Release status: completed | draft | halted | inProgress (default: ${DEFAULT_STATUS})
  --user-fraction <0..1>        Rollout fraction when --status inProgress
  --release-name <name>         Optional release name in Play Console
  --release-notes <text>        Optional release notes (en-US)
  --release-notes-lang <lang>   BCP-47 language code for release notes (default: en-US)
  --service-account <path>      Path to service account JSON key
  --changes-not-sent-for-review Pass changesNotSentForReview=true on commit
  --dry-run                     Upload bundle and stage track, then discard edit instead of committing
  --check                       Verify OAuth token + Play Console API access without uploading
  -h, --help                    Show this help message
`);
}

export function parseArgs(argv) {
  const opts = {
    packageName: DEFAULT_PACKAGE,
    track: DEFAULT_TRACK,
    status: DEFAULT_STATUS,
    releaseNotesLang: "en-US",
    changesNotSentForReview: false,
    dryRun: false,
    check: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) {
        throw new Error(`Missing value for ${arg}`);
      }
      return argv[++i];
    };

    switch (arg) {
      case "-h":
      case "--help":
        opts.help = true;
        break;
      case "--aab":
        opts.aab = next();
        break;
      case "--version-code":
        opts.versionCode = next();
        break;
      case "--package":
        opts.packageName = next();
        break;
      case "--track":
        opts.track = next();
        break;
      case "--status":
        opts.status = next();
        break;
      case "--user-fraction":
        opts.userFraction = Number(next());
        break;
      case "--release-name":
        opts.releaseName = next();
        break;
      case "--release-notes":
        opts.releaseNotes = next();
        break;
      case "--release-notes-lang":
        opts.releaseNotesLang = next();
        break;
      case "--service-account":
        opts.serviceAccount = next();
        break;
      case "--changes-not-sent-for-review":
        opts.changesNotSentForReview = true;
        break;
      case "--dry-run":
        opts.dryRun = true;
        break;
      case "--check":
        opts.check = true;
        break;
      default:
        throw new Error(`Unknown option: ${arg}`);
    }
  }

  return opts;
}

function expandHome(p) {
  if (!p) return p;
  if (p === "~") return os.homedir();
  if (p.startsWith("~/")) return path.join(os.homedir(), p.slice(2));
  return p;
}

function readGradleProperty(propName) {
  const propsPath = path.join(os.homedir(), ".gradle", "gradle.properties");
  if (!fs.existsSync(propsPath)) return undefined;
  const lines = fs.readFileSync(propsPath, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
    const eqIdx = trimmed.indexOf("=");
    if (eqIdx === -1) continue;
    const key = trimmed.slice(0, eqIdx).trim();
    if (key === propName) {
      return trimmed.slice(eqIdx + 1).trim();
    }
  }
  return undefined;
}

export function loadServiceAccountCredentials(explicitPath) {
  const envJson = process.env.ZUNO_PLAY_SERVICE_ACCOUNT_JSON;
  if (envJson && envJson.trim().startsWith("{")) {
    return JSON.parse(envJson);
  }

  const candidatePath = expandHome(
    explicitPath ||
      process.env.ZUNO_PLAY_SERVICE_ACCOUNT_FILE ||
      process.env.GOOGLE_APPLICATION_CREDENTIALS ||
      readGradleProperty("zuno.play.serviceAccountFile") ||
      DEFAULT_CREDENTIALS
  );

  if (!fs.existsSync(candidatePath)) {
    throw new Error(
      `Service account JSON not found at ${candidatePath}. Pass --service-account or set ZUNO_PLAY_SERVICE_ACCOUNT_FILE.`
    );
  }

  return JSON.parse(fs.readFileSync(candidatePath, "utf8"));
}

function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input, "utf8");
  return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function createSignedJwt(credentials, nowSec = Math.floor(Date.now() / 1000)) {
  if (!credentials.client_email || !credentials.private_key) {
    throw new Error("Invalid service account JSON: missing client_email or private_key.");
  }

  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: credentials.client_email,
    scope: SCOPE,
    aud: credentials.token_uri || TOKEN_URL,
    iat: nowSec,
    exp: nowSec + 3600,
  };

  const signingInput = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}`;
  const signature = crypto.sign("RSA-SHA256", Buffer.from(signingInput, "utf8"), credentials.private_key);
  return `${signingInput}.${base64UrlEncode(signature)}`;
}

async function fetchAccessToken(credentials) {
  const assertion = createSignedJwt(credentials);
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });

  const res = await fetch(credentials.token_uri || TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  const json = await res.json();
  if (!res.ok || !json.access_token) {
    throw new Error(`OAuth token exchange failed (${res.status}): ${JSON.stringify(json)}`);
  }
  return json.access_token;
}

async function apiRequest(url, { method = "GET", token, headers = {}, body } = {}) {
  const res = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...headers,
    },
    body,
  });

  const text = await res.text();
  let parsed;
  try {
    parsed = text ? JSON.parse(text) : {};
  } catch {
    parsed = { raw: text };
  }

  if (!res.ok) {
    const errMsg = parsed?.error?.message || text || `HTTP ${res.status}`;
    const err = new Error(`Play API ${method} ${url} failed (${res.status}): ${errMsg}`);
    err.status = res.status;
    err.payload = parsed;
    throw err;
  }

  return parsed;
}

async function createEdit(packageName, token) {
  return apiRequest(`${API_BASE}/applications/${encodeURIComponent(packageName)}/edits`, {
    method: "POST",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({}),
  });
}

async function deleteEdit(packageName, editId, token) {
  await fetch(`${API_BASE}/applications/${encodeURIComponent(packageName)}/edits/${encodeURIComponent(editId)}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => {});
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help) {
    printHelp();
    return;
  }

  if (!opts.check && !opts.aab && !opts.versionCode) {
    throw new Error("Missing --aab <path> or --version-code <n> (or pass --check to verify access).");
  }

  if (opts.aab && !fs.existsSync(opts.aab)) {
    throw new Error(`AAB file not found: ${opts.aab}`);
  }

  const credentials = loadServiceAccountCredentials(opts.serviceAccount);
  console.log(`Authenticating as ${credentials.client_email} for ${opts.packageName}...`);
  const token = await fetchAccessToken(credentials);
  console.log("OAuth2 access token acquired.");

  let edit;
  try {
    edit = await createEdit(opts.packageName, token);
  } catch (err) {
    if (err.status === 401 || err.status === 403 || err.status === 404) {
      console.error(`\n${err.message}`);
      console.error(
        `\nMake sure "${credentials.client_email}" is invited in Google Play Console -> Users and permissions\n` +
          `with Release permissions for "${opts.packageName}" (and that at least one initial APK/AAB exists in Play Console).`
      );
      process.exit(1);
    }
    throw err;
  }

  const editId = edit.id;
  console.log(`Created Play edit: ${editId}`);

  try {
    if (opts.check) {
      const tracks = await apiRequest(
        `${API_BASE}/applications/${encodeURIComponent(opts.packageName)}/edits/${encodeURIComponent(editId)}/tracks`,
        { token }
      );
      console.log("Play Console access verified! Current tracks:");
      for (const track of tracks.tracks || []) {
        const summary = (track.releases || [])
          .map((r) => `[${r.status}: ${(r.versionCodes || []).join(",")}]`)
          .join(" ");
        console.log(`  - ${track.track}: ${summary || "(no releases)"}`);
      }
      await deleteEdit(opts.packageName, editId, token);
      return;
    }

    let targetVersionCode = opts.versionCode ? String(opts.versionCode) : null;
    if (!targetVersionCode) {
      const aabBuffer = fs.readFileSync(opts.aab);
      console.log(`Uploading ${opts.aab} (${(aabBuffer.length / (1024 * 1024)).toFixed(2)} MB)...`);
      const bundle = await apiRequest(
        `${UPLOAD_BASE}/applications/${encodeURIComponent(opts.packageName)}/edits/${encodeURIComponent(editId)}/bundles?uploadType=media`,
        {
          method: "POST",
          token,
          headers: {
            "Content-Type": "application/octet-stream",
            "Content-Length": String(aabBuffer.length),
          },
          body: aabBuffer,
        }
      );
      targetVersionCode = String(bundle.versionCode);
      console.log(`Uploaded bundle versionCode=${bundle.versionCode} (sha256=${bundle.sha256}).`);
    } else {
      console.log(`Promoting existing versionCode=${targetVersionCode} to track "${opts.track}"...`);
    }

    const release = {
      status: opts.status,
      versionCodes: [targetVersionCode],
    };
    if (opts.releaseName) release.name = opts.releaseName;
    if (opts.status === "inProgress" && Number.isFinite(opts.userFraction)) {
      release.userFraction = opts.userFraction;
    }
    if (opts.releaseNotes) {
      release.releaseNotes = [{ language: opts.releaseNotesLang, text: opts.releaseNotes }];
    }

    await apiRequest(
      `${API_BASE}/applications/${encodeURIComponent(opts.packageName)}/edits/${encodeURIComponent(editId)}/tracks/${encodeURIComponent(opts.track)}`,
      {
        method: "PUT",
        token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          track: opts.track,
          releases: [release],
        }),
      }
    );
    console.log(`Assigned versionCode ${targetVersionCode} to track "${opts.track}" (${opts.status}).`);

    if (opts.dryRun) {
      await deleteEdit(opts.packageName, editId, token);
      console.log("Dry run complete; discarded edit without committing.");
      return;
    }

    const query = opts.changesNotSentForReview ? "?changesNotSentForReview=true" : "";
    await apiRequest(
      `${API_BASE}/applications/${encodeURIComponent(opts.packageName)}/edits/${encodeURIComponent(editId)}:commit${query}`,
      {
        method: "POST",
        token,
        headers: { "Content-Type": "application/json" },
      }
    );
    console.log(`Committed edit ${editId}. Release is now on Google Play "${opts.track}" track.`);
  } catch (err) {
    await deleteEdit(opts.packageName, editId, token);
    if (String(err.message).includes("draft")) {
      console.error(
        `\nTip: If your app in Google Play Console is still in "Draft" state, re-run with --status draft`
      );
    }
    throw err;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  main().catch((err) => {
    console.error(err.message || err);
    process.exit(1);
  });
}
