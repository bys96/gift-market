import type { NextConfig } from "next";

type RemotePattern = NonNullable<
  NonNullable<NextConfig["images"]>["remotePatterns"]
>[number];

const storageBaseUrl = process.env.NEXT_PUBLIC_STORAGE_BASE_URL?.trim();

function createStorageRemotePattern(): RemotePattern | null {
  if (!storageBaseUrl) return null;

  try {
    const url = new URL(storageBaseUrl);

    if (url.protocol !== "http:" && url.protocol !== "https:") return null;

    const basePath = url.pathname.replace(/\/+$/, "");

    return {
      protocol: url.protocol.slice(0, -1) as "http" | "https",
      hostname: url.hostname,
      port: url.port,
      pathname: basePath ? `${basePath}/**` : "/**",
    };
  } catch {
    return null;
  }
}

function isLocalOrPrivateStorageUrl(): boolean {
  if (!storageBaseUrl) return false;

  try {
    const { hostname } = new URL(storageBaseUrl);

    return (
      hostname === "localhost" ||
      hostname === "127.0.0.1" ||
      hostname === "::1" ||
      hostname.startsWith("10.") ||
      hostname.startsWith("192.168.") ||
      /^172\.(1[6-9]|2\d|3[01])\./.test(hostname)
    );
  } catch {
    return false;
  }
}

const storageRemotePattern = createStorageRemotePattern();

const remotePatterns: RemotePattern[] = [
  {
    protocol: "https",
    hostname: "lh3.googleusercontent.com",
  },
  {
    protocol: "http",
    hostname: "img1.kakaocdn.net",
  },
  {
    protocol: "https",
    hostname: "img1.kakaocdn.net",
  },
  {
    protocol: "http",
    hostname: "localhost",
    port: "9000",
    pathname: "/**",
  },
];

if (storageRemotePattern) remotePatterns.push(storageRemotePattern);

const nextConfig: NextConfig = {
  images: {
    dangerouslyAllowLocalIP:
      process.env.NODE_ENV !== "production" || isLocalOrPrivateStorageUrl(),
    remotePatterns,
  },
};

export default nextConfig;
