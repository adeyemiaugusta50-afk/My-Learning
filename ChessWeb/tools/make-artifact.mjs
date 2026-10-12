// Turns the single-file build (dist-single/index.html) into a page fragment for hosts that wrap the page themselves:
// <title>, <style>, the app root and the inline module script, with no <html>/<head>/<body>.
// Usage: npm run build:single && node tools/make-artifact.mjs   ->  dist-single/fragment.html
import { readFileSync, writeFileSync } from 'node:fs'

const html = readFileSync('dist-single/index.html', 'utf8')
const title = /<title>([\s\S]*?)<\/title>/.exec(html)?.[1] ?? 'Casual Chess'
const style = [...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1]).join('\n')
const script = /<script type="module"[^>]*>([\s\S]*?)<\/script>/.exec(html)?.[1]
if (!script) throw new Error('inline module script not found; run `npm run build:single` first')
writeFileSync('dist-single/fragment.html', `<title>${title}</title>\n<style>${style}</style>\n<div id="app"></div>\n<script type="module">${script.replaceAll('</script', '<\\/script')}</script>\n`)
console.log('wrote dist-single/fragment.html')
