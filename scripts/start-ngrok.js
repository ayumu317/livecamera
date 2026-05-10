const fs = require("fs");
const http = require("http");
const path = require("path");
const { spawn } = require("child_process");

const PROJECT_ROOT = path.resolve(__dirname, "..");
const NGROK_API_URL = process.env.NGROK_API_URL || "http://127.0.0.1:4040/api/tunnels";
const BACKEND_PORT = process.env.NGROK_BACKEND_PORT || "8080";
const PROPERTY_NAME = "NGROK_BASE_URL";
const START_TIMEOUT_MS = Number(process.env.NGROK_START_TIMEOUT_MS || 30000);
const POLL_INTERVAL_MS = 1000;

function requestJson(url) {
  return new Promise((resolve, reject) => {
    const request = http.get(url, { timeout: 2000 }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`ngrok API returned HTTP ${response.statusCode}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(new Error(`ngrok API returned invalid JSON: ${error.message}`));
        }
      });
    });

    request.on("timeout", () => {
      request.destroy(new Error("ngrok API request timed out"));
    });
    request.on("error", reject);
  });
}

function getHttpsPublicUrl(tunnelsResponse) {
  const tunnels = Array.isArray(tunnelsResponse && tunnelsResponse.tunnels)
    ? tunnelsResponse.tunnels
    : [];
  const tunnel = tunnels.find((item) => {
    return typeof item.public_url === "string" && item.public_url.startsWith("https://");
  });
  return tunnel ? tunnel.public_url.replace(/\/+$/, "") : "";
}

async function readCurrentPublicUrl() {
  try {
    return getHttpsPublicUrl(await requestJson(NGROK_API_URL));
  } catch (_) {
    return "";
  }
}

function startNgrok() {
  const child = spawn("ngrok", ["http", BACKEND_PORT], {
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  });
  child.on("error", (error) => {
    console.error(`Failed to start ngrok: ${error.message}`);
  });
  child.unref();
}

async function waitForPublicUrl() {
  const start = Date.now();
  while (Date.now() - start < START_TIMEOUT_MS) {
    const publicUrl = await readCurrentPublicUrl();
    if (publicUrl) {
      return publicUrl;
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }
  throw new Error(`ngrok did not expose an HTTPS tunnel within ${START_TIMEOUT_MS}ms`);
}

function upsertKeyValueFile(filePath, key, value, separator) {
  let content = "";
  if (fs.existsSync(filePath)) {
    content = fs.readFileSync(filePath, "utf8");
  }

  const newline = content.includes("\r\n") ? "\r\n" : "\n";
  const lines = content ? content.split(/\r?\n/) : [];
  let updated = false;
  const nextLines = lines.map((line) => {
    const trimmed = line.trimStart();
    if (!trimmed.startsWith("#") && trimmed.startsWith(`${key}${separator}`)) {
      updated = true;
      return `${key}${separator}${value}`;
    }
    return line;
  });

  if (!updated) {
    if (nextLines.length > 0 && nextLines[nextLines.length - 1] !== "") {
      nextLines.push("");
    }
    nextLines.push(`${key}${separator}${value}`);
  }

  fs.writeFileSync(filePath, nextLines.join(newline).replace(/\s*$/, newline), "utf8");
}

function writeLocalProperties(publicUrl) {
  const localPropertiesPath = path.join(PROJECT_ROOT, "local.properties");
  upsertKeyValueFile(localPropertiesPath, PROPERTY_NAME, publicUrl, "=");
  return localPropertiesPath;
}

function shouldSkipDirectory(name) {
  return [
    ".git",
    ".gradle",
    ".idea",
    "build",
    "node_modules",
    "target",
    "tmp_tools",
  ].includes(name);
}

function findConfigDirectories(rootDir, maxDepth = 3) {
  const result = new Set();

  function visit(directory, depth) {
    if (depth > maxDepth) {
      return;
    }

    const entries = fs.existsSync(directory)
      ? fs.readdirSync(directory, { withFileTypes: true })
      : [];
    const hasAppConfig = entries.some((entry) => {
      return entry.isFile() && ["package.json", "pom.xml"].includes(entry.name);
    });
    const hasExistingEnv = entries.some((entry) => {
      return entry.isFile() && entry.name === ".env";
    });

    if (hasAppConfig || hasExistingEnv) {
      result.add(directory);
    }

    for (const entry of entries) {
      if (entry.isDirectory() && !shouldSkipDirectory(entry.name)) {
        visit(path.join(directory, entry.name), depth + 1);
      }
    }
  }

  visit(rootDir, 0);
  return [...result];
}

function writeEnvFiles(publicUrl) {
  return findConfigDirectories(PROJECT_ROOT)
    .map((directory) => path.join(directory, ".env"))
    .map((envPath) => {
      upsertKeyValueFile(envPath, PROPERTY_NAME, publicUrl, "=");
      return envPath;
    });
}

async function main() {
  let publicUrl = await readCurrentPublicUrl();
  if (publicUrl) {
    console.log(`Reusing existing ngrok tunnel: ${publicUrl}`);
  } else {
    console.log(`Starting ngrok: ngrok http ${BACKEND_PORT}`);
    startNgrok();
    publicUrl = await waitForPublicUrl();
    console.log(`ngrok tunnel is ready: ${publicUrl}`);
  }

  const localPropertiesPath = writeLocalProperties(publicUrl);
  const envFiles = writeEnvFiles(publicUrl);

  console.log(`Wrote ${PROPERTY_NAME} to ${localPropertiesPath}`);
  for (const envFile of envFiles) {
    console.log(`Wrote ${PROPERTY_NAME} to ${envFile}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
