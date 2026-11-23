// Generates the files for coding agents that the site serves next to the pages:
//
// - static/skills/: a copy of the Agent Skills in skills/ at the top of the repository, so that
//   existing projects can download them (generated projects get them from the archetype);
// - static/llms.txt: an index of the documentation in the llms.txt format (https://llmstxt.org),
//   in the order of the sidebar, with the first sentence of each page as its description.
//
// Runs as "prebuild"/"prestart"; both outputs are generated, not checked in.

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import sidebars from '../sidebars.js';

const docsDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const repoDir = path.dirname(docsDir);
const staticDir = path.join(docsDir, 'static');
const SITE = 'https://cloudhopper.eu';

// Agent Skills
const skillsTarget = path.join(staticDir, 'skills');
fs.rmSync(skillsTarget, {recursive: true, force: true});
fs.cpSync(path.join(repoDir, 'skills'), skillsTarget, {recursive: true});
const skills = fs.readdirSync(skillsTarget).filter(name =>
    fs.existsSync(path.join(skillsTarget, name, 'SKILL.md')));

// llms.txt
function readDoc(id) {
  for (const ext of ['.md', '.mdx']) {
    const file = path.join(docsDir, 'docs', id + ext);
    if (fs.existsSync(file)) return fs.readFileSync(file, 'utf8');
  }
  throw new Error(`sidebar refers to a missing page: ${id}`);
}

function describe(id, label) {
  let text = readDoc(id);
  const front = {};
  const match = text.match(/^---\n([\s\S]*?)\n---\n/);
  if (match) {
    for (const line of match[1].split('\n')) {
      const kv = line.match(/^(\w+):\s*(.*)$/);
      if (kv) front[kv[1]] = kv[2].replace(/^['"]|['"]$/g, '');
    }
    text = text.slice(match[0].length);
  }
  const heading = text.match(/^# (.+)$/m);
  const title = label ?? front.title ?? heading?.[1] ?? id;
  let description = front.description;
  if (!description) {
    // the first paragraph of prose after the heading
    const body = heading ? text.slice(text.indexOf(heading[0]) + heading[0].length) : text;
    const paragraph = body.split(/\n\s*\n/).map(p => p.trim())
        .find(p => p && !/^(#|import |<|```|---|\||[-*] |\*\*[^*]+\*\*$|:::)/.test(p));
    if (paragraph) {
      const plain = paragraph.replace(/\s+/g, ' ')
          .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1').replace(/\*\*/g, '');
      description = plain.match(/^.+?[.:](?=\s|$)/)?.[0] ?? plain;
      description = description.replace(/:$/, '.');
    }
  }
  const slug = id === 'intro' ? 'intro' : id;
  return `- [${title}](${SITE}/docs/${slug})${description ? `: ${description}` : ''}`;
}

const sections = [];
let loose = [];
function walk(items, out, prefix) {
  for (const item of items) {
    if (typeof item === 'string') out.push(describe(item));
    else if (item.type === 'doc') out.push(describe(item.id, prefix && item.label ? `${prefix}: ${item.label}` : undefined));
    else if (item.type === 'category') walk(item.items, out, item.label);
  }
}
for (const item of sidebars.documentationSidebar) {
  if (item.type === 'category') {
    const lines = [];
    walk(item.items, lines);
    sections.push(`## ${item.label}\n\n${lines.join('\n')}`);
  } else {
    walk([item], loose);
  }
}

const llms = `# Dukehoff Cloudhopper

> Cloudhopper turns annotated Java methods into serverless functions. An annotation processor
> generates the handler classes and the deployment configuration (Terraform, a Helm chart or a
> Spring Boot application) for AWS Lambda, Azure Functions, Google Cloud Functions, Knative,
> Scaleway Serverless Containers or Spring Boot. A Maven profile selects the target; the code
> stays the same.

Functions are plain methods with \`@Function\` and a trigger (\`@HttpTrigger\`, \`@ScheduledTrigger\`,
\`@EventTrigger\`). Do not write handler classes, cloud SDK calls or Terraform for them, and do not
edit \`target/\`: the build generates those files. Configuration comes from \`@ConfigProperty\`,
messages are sent through \`@Queue\` publishers.

${loose.join('\n')}

${sections.join('\n\n')}

## Coding agents

${skills.map(name => `- [Agent Skill \`${name}\`](${SITE}/skills/${name}/SKILL.md): recipes for writing, building and deploying Cloudhopper functions, in the Agent Skills format.`).join('\n')}

## Optional

- [API reference (Javadoc)](${SITE}/api/index.html): the annotations and runtime types.
- [Source code](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper): the repository on GitHub.
`;

fs.writeFileSync(path.join(staticDir, 'llms.txt'), llms);
console.log(`agent files: llms.txt, skills ${skills.join(', ')}`);
