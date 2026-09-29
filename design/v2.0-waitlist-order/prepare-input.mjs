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
console.log(`Prepared ${resolve(target)}; Pencil authentication and native validation are still required.`);
