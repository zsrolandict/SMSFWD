import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
const output = process.argv[2] || '/workspace/artifacts/SMSFWD-bemutato.html';
let html = await readFile('dist/index.html', 'utf8');
const jsName = html.match(/<script[^>]*src="([^"]+)"[^>]*><\/script>/)?.[1];
const cssName = html.match(/<link[^>]*href="([^"]+\.css)"[^>]*>/)?.[1];
if (!jsName || !cssName) throw new Error('The production JS/CSS entry points were not found.');
const script = (await readFile(path.join('dist', jsName), 'utf8')).replaceAll('</script', '<\\/script');
let css = await readFile(path.join('dist', cssName), 'utf8');
const fonts = [...new Set([...css.matchAll(/url\(([^)]+)\)/g)].map(m => m[1].replaceAll('"', '').replaceAll("'", '')))];
for (const font of fonts) {
  if (!font.startsWith('/assets/')) throw new Error(`Unexpected external asset: ${font}`);
  const data = await readFile(path.join('dist', font));
  const type = font.endsWith('.woff2') ? 'font/woff2' : 'font/woff';
  css = css.replaceAll(font, `data:${type};base64,${data.toString('base64')}`);
}
html = html.replace(/<script[^>]*src="[^"]+"[^>]*><\/script>/, () => `<script type="module">${script}</script>`);
html = html.replace(/<link[^>]*href="[^"]+\.css"[^>]*>/, () => `<style>${css}</style>`);
await mkdir(path.dirname(output), { recursive: true });
await writeFile(output, html);
console.log(`Offline demo exported: ${output}`);
