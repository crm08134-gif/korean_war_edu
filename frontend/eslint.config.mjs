import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // maplibre-gl에서 복사해 온 워커 파일 (scripts/copy-maplibre-worker.mjs)
    "public/maplibre-gl-worker.mjs",
  ]),
]);

export default eslintConfig;
