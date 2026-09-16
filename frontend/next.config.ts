import type { NextConfig } from "next";

// Export estatico: a aplicacao e toda client-side (token no navegador, REST + WebSocket),
// entao o build gera HTML/JS estaticos que o Spring Boot serve em src/main/resources/static.
const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
};

export default nextConfig;
