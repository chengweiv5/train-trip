import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const target = process.argv[2];
if (!target) throw new Error('Usage: node prepare-input.mjs /path/to/pencil-input.txt');
const source = ['helpers.js', 'screens.js']
  .map(name => readFileSync(new URL(name, import.meta.url), 'utf8')).join('\n');
const exportDir = fileURLToPath(new URL('../reference/v2.0-waitlist-order/pencil/', import.meta.url));
const command = input => `execute(${JSON.stringify({ input })})`;
writeFileSync(resolve(target), [
  'get_app_state()',
  command('Print(GetVariables())'),
  command(source),
  command('Get((n,c)=>{if(c.problems)Print(n.name,c.problems);if(n.type==="frame"&&c.depth===0)Print(n.name,c.bounds)})'),
  'save()',
  command(`Export(screens,"png",${JSON.stringify(exportDir)});Export(screens,"pdf",${JSON.stringify(exportDir)})`),
  'exit()',
  '',
].join('\n'));
console.log(`Prepared ${resolve(target)} for an EMPTY document only; do not rerun on the delivered 17-screen canvas. Native rendering, save, and reopen validation are required. Desktop MCP does not depend on Pencil CLI authentication.`);
