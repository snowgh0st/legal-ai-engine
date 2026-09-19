import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Only enable standalone mode when explicitly building via Docker
  ...(process.env.BUILD_STANDALONE === 'true' && { output: 'standalone' }),
};

export default nextConfig;