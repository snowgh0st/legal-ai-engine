import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Proxy /api/* requests directly to your Cloud Run Spring Boot backend
  if (pathname.startsWith('/api/')) {
    const backendUrl = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;
    
    if (!backendUrl) {
      return new NextResponse('Backend URL not configured', { status: 500 });
    }

    const targetUrl = new URL(pathname + request.nextUrl.search, backendUrl);
    
    return NextResponse.rewrite(targetUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/api/:path*'],
};