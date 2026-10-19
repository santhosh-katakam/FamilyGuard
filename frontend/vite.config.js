import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.VITE_DEV_API_TARGET || "http://localhost:8080";
  return {
    plugins: [react(), tailwindcss()],
    test: {
      // Node 26's host storage must not shadow jsdom's browser storage.
      poolOptions: { forks: { execArgv: ["--no-experimental-webstorage"] } },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            charts: ["recharts"],
            maps: ["leaflet", "react-leaflet"],
          },
        },
      },
    },
    server: {
      proxy: {
        "/api": apiTarget,
        "/ws": { target: apiTarget.replace(/^http/, "ws"), ws: true },
      },
    },
  };
});
