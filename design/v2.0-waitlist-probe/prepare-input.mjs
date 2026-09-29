import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

// Prepare native Pencil commands without invoking an agent or bypassing login.
const source = readFileSync(new URL('./canvas.js', import.meta.url), 'utf8');
const target = process.argv[2];
if (!target) throw new Error('Usage: node prepare-input.mjs /path/to/pencil-input.txt');
const exportDir = fileURLToPath(new URL('../reference/v2.0-waitlist-probe/pencil/', import.meta.url));
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
console.log(`Prepared ${resolve(target)}. Inspect CLI output and fix any errors before accepting the saved design.`);
