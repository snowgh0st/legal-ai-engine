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

    // 1. Check if the client already sent an Authorization header
    let token = req.headers.get('authorization')?.replace('Bearer ', '');

    // 2. If not provided by client, try extracting from Clerk session
    if (!token) {
      const { getToken } = await auth();
      token = (await getToken()) || undefined;
    }

    // 3. If still no token, reject request
    if (!token) {
      return new NextResponse('Unauthorized: Missing Clerk Session Token', { status: 401 });
    }

    const cleanBackendUrl = backendUrl.replace(/\/$/, '');
    const targetUrl = new URL(pathname + search, cleanBackendUrl);

    // Forward request with Bearer token attached
    const requestHeaders = new Headers(req.headers);
    requestHeaders.set('Authorization', `Bearer ${token}`);

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