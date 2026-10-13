import { createServer, request } from 'node:http'

const port = Number(process.env.PORT || 8002)
const upstream = new URL(process.env.OCR_UPSTREAM_URL || 'http://127.0.0.1:8001')
let injectedFailure = false

const server = createServer((incoming, outgoing) => {
  if (incoming.method === 'POST' && incoming.url?.split('?')[0] === '/api/ocr/tasks' && !injectedFailure) {
    injectedFailure = true
    outgoing.writeHead(503, { 'content-type': 'application/json' })
    outgoing.end(JSON.stringify({ code: 'TEMPORARY_UNAVAILABLE', message: 'one-time verification failure' }))
    return
  }

  const upstreamRequest = request({
    hostname: upstream.hostname,
    port: upstream.port,
    path: incoming.url,
    method: incoming.method,
    headers: { ...incoming.headers, host: upstream.host },
  }, (upstreamResponse) => {
    outgoing.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(outgoing)
  })

  upstreamRequest.on('error', () => {
    if (!outgoing.headersSent) outgoing.writeHead(502, { 'content-type': 'application/json' })
    outgoing.end(JSON.stringify({ code: 'UPSTREAM_UNAVAILABLE', message: 'OCR upstream unavailable' }))
  })
  incoming.pipe(upstreamRequest)
})

server.listen(port, '127.0.0.1')
