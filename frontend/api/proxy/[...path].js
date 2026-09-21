// Vercel serverless function (Node.js runtime) acting as a same-origin reverse proxy in front of
// the real backend. Everything under the paths listed in vercel.json's `rewrites` lands here
// instead of at a real backend URL, so from the browser's point of view every REST call stays
// first-party - the guest-session cookie (SameSite=None; Secure) is then a normal same-site
// cookie, not a third-party one, and isn't subject to Safari ITP / Chrome's third-party phase-out.
//
// The real backend's location is read from `BACKEND_ORIGIN` (a plain Vercel project environment
// variable, not build-time/VITE_-prefixed - this file runs server-side on Vercel's infrastructure,
// never shipped to the browser). Moving the backend to a different host later is exactly one env
// var change in the Vercel dashboard, no code/file change and no rebuild of this function required.
//
// What this does NOT cover: the multiplayer WebSocket (`/ws`) and the Google OAuth2 redirect flow
// (`/oauth2/*`, `/authCallback`) both still go directly to the backend's own origin - see
// `useMultiplayerSocket.js` and the "Sign in with Google" links respectively, and their doc
// comments for why each is excluded here.

export const config = {
  api: {
    bodyParser: false, // forward the raw request body ourselves, untouched
  },
};

/** Buffers the incoming request body exactly as received, for forwarding as-is. */
async function readRawBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export default async function handler(req, res) {
  const backendOrigin = process.env.BACKEND_ORIGIN;
  if (!backendOrigin) {
    res.status(500).json({ message: 'BACKEND_ORIGIN is not configured on this deployment' });
    return;
  }

  // Vercel populates this catch-all route's matched segments under the literal query key
  // "...path" (taken verbatim from this file's name, [...path].js) for a plain Serverless
  // Function - NOT under "path". The rest of the incoming query string (e.g. ?period=week)
  // arrives alongside it under req.query too, so "...path" must be stripped back out before
  // forwarding, or it would leak into the backend request as a bogus query param.
  const rawSegments = req.query['...path'];
  const segments = Array.isArray(rawSegments) ? rawSegments : rawSegments ? [rawSegments] : [];
  const path = `/${segments.join('/')}`;

  const forwardUrl = new URL(req.url, 'http://placeholder');
  forwardUrl.searchParams.delete('...path');
  const search = forwardUrl.search;
  const targetUrl = `${backendOrigin}${path}${search}`;

  const forwardHeaders = { ...req.headers };
  delete forwardHeaders.host;
  delete forwardHeaders.connection;
  delete forwardHeaders['content-length'];

  const hasBody = req.method !== 'GET' && req.method !== 'HEAD';

  let backendResponse;
  try {
    backendResponse = await fetch(targetUrl, {
      method: req.method,
      headers: forwardHeaders,
      body: hasBody ? await readRawBody(req) : undefined,
      redirect: 'manual',
    });
  } catch {
    res.status(502).json({ message: 'Could not reach the backend' });
    return;
  }

  // fetch() already decodes the response body, so an original content-encoding/transfer-encoding
  // header would be a lie if forwarded as-is - the browser would try to re-decode already-decoded
  // bytes. Set-Cookie needs its own path since multiple cookies can't survive a plain header copy
  // (Headers collapses repeated entries into one comma-joined string via forEach/get).
  backendResponse.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (lower === 'content-encoding' || lower === 'transfer-encoding' || lower === 'set-cookie') return;
    res.setHeader(key, value);
  });
  const setCookie = backendResponse.headers.getSetCookie?.() ?? [];
  if (setCookie.length > 0) {
    res.setHeader('Set-Cookie', setCookie);
  }

  res.status(backendResponse.status);
  res.send(Buffer.from(await backendResponse.arrayBuffer()));
}