const fs = require("node:fs");
const path = require("node:path");
const esbuild = require("esbuild");

const root = path.resolve(__dirname, "..");
const output = path.join(root, "www");
const files = ["index.html", "styles.css", "app.js"];

async function build() {
  fs.rmSync(output, { recursive: true, force: true });
  fs.mkdirSync(output, { recursive: true });

  for (const file of files) {
    fs.copyFileSync(path.join(root, file), path.join(output, file));
  }
  fs.cpSync(path.join(root, "assets"), path.join(output, "assets"), { recursive: true });

  await esbuild.build({
    entryPoints: [path.join(__dirname, "native-bridge.js")],
    bundle: true,
    platform: "browser",
    format: "iife",
    target: "es2020",
    outfile: path.join(output, "capacitor-bridge.js"),
  });

  const indexPath = path.join(output, "index.html");
  const index = fs.readFileSync(indexPath, "utf8");
  const appScript = '    <script src="app.js" defer></script>';
  if (!index.includes(appScript)) {
    throw new Error("Could not locate the app script in index.html to inject the Capacitor bridge.");
  }
  fs.writeFileSync(indexPath, index.replace(appScript, '    <script src="capacitor-bridge.js" defer></script>\n' + appScript));
}

build().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
