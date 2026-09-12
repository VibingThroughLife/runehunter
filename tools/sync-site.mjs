import { cp, copyFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// GitHub Pages' docs/ is canonical; the root entry remains a matching local preview.
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
await copyFile(path.join(repo, 'docs/index.html'), path.join(repo, 'runehunter-index.html'));
await cp(path.join(repo, 'docs/assets'), path.join(repo, 'assets'), { recursive: true });
console.log('Website HTML and local runtime assets mirrored.');
