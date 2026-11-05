import https from 'node:https';
import { readFile } from 'node:fs/promises';
import { createBff } from './gateway.mjs';

// No .env reads or deployment defaults. Keep explicit JSON and PEMs outside Git.
async function main() {
  if (process.argv.length !== 4 || process.argv[2] !== '--config') throw new Error('Explicit --config file is required');
  let options;
  try { options = JSON.parse(await readFile(process.argv[3], 'utf8')); }
  catch { throw new Error('Cannot read explicit BFF config file'); }
  const handler = createBff(options);
  const origin = new URL(options.publicOrigin);
  let server;
  try {
    if (!options.tlsKeyPem || !options.tlsCertPem) throw new Error();
    server = https.createServer({ key: options.tlsKeyPem, cert: options.tlsCertPem, minVersion: 'TLSv1.2', maxHeaderSize: 16_384 }, handler);
  } catch { throw new Error('Invalid synthetic HTTPS config'); }
  server.requestTimeout = 20_000;
  server.headersTimeout = 20_000;
  server.on('error', () => { console.error('Local BFF could not listen'); process.exitCode = 1; });
  const host = origin.hostname === '[::1]' ? '::1' : '127.0.0.1';
  server.listen(Number(origin.port || 443), host, () => console.log(`Local BFF listening at ${origin.origin}`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => { server.closeAllConnections(); server.close(); });
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
