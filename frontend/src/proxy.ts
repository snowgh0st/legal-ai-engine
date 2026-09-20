import { clerkMiddleware } from '@clerk/nextjs/server';
import { NextResponse } from 'next/server';

// Explicit list of public paths (sign-in, sign-up, static public assets)
const PUBLIC_PATHS = ['/sign-in', '/sign-up'];

export default clerkMiddleware(async (auth, req) => {
  const { pathname, search } = req.nextUrl;

  // Check if current route is in public paths list
  const isPublicPath = PUBLIC_PATHS.some((path) => pathname.startsWith(path));

  // 1. FRONTEND ROUTE PROTECTION
  // If user is unauthenticated, not on a public path, and not calling an API:
  // Redirect immediately to Clerk sign-in
  const { userId } = await auth();
  if (!userId && !isPublicPath && !pathname.startsWith('/api/')) {
    const { redirectToSignIn } = await auth();
    return redirectToSignIn();
  }

  // 2. BACKEND API PROXY (/api/v1/...)
  if (pathname.startsWith('/api/')) {
    const backendUrl = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

    if (!backendUrl) {
      return new NextResponse('Backend URL not configured', { status: 500 });
    }

    // A. Extract raw token from client header if sent directly
    let rawToken: string | null = null;
    const rawAuthHeader = req.headers.get('authorization');

    if (rawAuthHeader) {
      // Case-insensitive regex strips any number of "Bearer " prefixes
      rawToken = rawAuthHeader.replace(/^Bearer\s+/i, '').trim();
    }

    // B. Fallback to Clerk server-side session token
    if (!rawToken) {
      const { getToken } = await auth();
      const sessionToken = await getToken();
      if (sessionToken) {
        rawToken = sessionToken.replace(/^Bearer\s+/i, '').trim();
      }
    }

    // C. If no token exists or session expired, reject early
    if (!rawToken || rawToken === 'null' || rawToken === 'undefined') {
      return new NextResponse('Unauthorized: Missing or expired Clerk session token', { status: 401 });
    }

    const cleanBackendUrl = backendUrl.replace(/\/$/, '');
    const targetUrl = new URL(pathname + search, cleanBackendUrl);

    // Forward request to GCP Cloud Run with a single clean Bearer header
    const requestHeaders = new Headers(req.headers);
    requestHeaders.set('Authorization', `Bearer ${rawToken}`);

    return NextResponse.rewrite(targetUrl, {
      request: {
        headers: requestHeaders,
      },
    });
  }

  return NextResponse.next();
});

export const config = {
  matcher: [
    '/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jwt|png|jpg|webp|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)',
    '/(api|trpc)(.*)',
  ],
};