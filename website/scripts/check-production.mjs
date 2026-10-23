import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import assert from 'node:assert/strict';

const files = await readdir('dist', { recursive: true });
assert(files.some(file => file.endsWith('.js')), 'Build must exist first');
assert(!files.some(file => /mockServiceWorker|fixtures|handlers/i.test(file)), 'Mock artifact in production');
for (const file of files.filter(file => /\.(js|html)$/.test(file))) {
  const content = await readFile(join('dist', file), 'utf8');
  assert(!/MOCK-CUSTOMER|mock-only-password|MOCK_PENDING|Mock Service Worker|mock-csrf-/.test(content), `Mock runtime/fixture in ${file}`);
}
console.log('Production bundle: no mock worker, runtime markers or fixture data.');
