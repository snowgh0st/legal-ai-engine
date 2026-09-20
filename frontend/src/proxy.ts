import { clerkMiddleware } from '@clerk/nextjs/server';
import { NextResponse } from 'next/server';

export default clerkMiddleware(async (auth, req) => {
  const { pathname, search } = req.nextUrl;

  // Intercept all backend API calls
  if (pathname.startsWith('/api/')) {
    const backendUrl = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

    if (!backendUrl) {
      return new NextResponse('Backend URL not configured', { status: 500 });
    }

    // 1. Extract raw Authorization header from incoming client request
    const rawAuthHeader = req.headers.get('authorization');
    let rawToken: string | null = null;

    if (rawAuthHeader) {
      // Strip any number of "Bearer " prefixes (case-insensitive)
      rawToken = rawAuthHeader.replace(/^Bearer\s+/i, '').trim();
    }

    // 2. If no valid header token present, extract from Clerk session
    if (!rawToken) {
      const { getToken } = await auth();
      const sessionToken = await getToken();
      if (sessionToken) {
        rawToken = sessionToken.replace(/^Bearer\s+/i, '').trim();
      }
    }

    // 3. Reject if no token found or if token is explicitly empty/null string
    if (!rawToken || rawToken === 'null' || rawToken === 'undefined') {
      return new NextResponse('Unauthorized: Missing Clerk Session Token', { status: 401 });
    }

    const cleanBackendUrl = backendUrl.replace(/\/$/, '');
    const targetUrl = new URL(pathname + search, cleanBackendUrl);

    // Guard against duplicated 'Bearer ' when forwarding to Cloud Run
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