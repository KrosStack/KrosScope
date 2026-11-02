const RELATIONS = {
  calls: ["calls", "called by"],
  references: ["refers to", "referred to by"],
  extends: ["extends", "extended by"],
  implements: ["implements", "implemented by"],
  overrides: ["overrides", "overridden by"],
  imports: ["imports", "imported by"],
  "type-uses": ["uses the type", "type used by"],
  actualizes: ["is the actual of", "expected by"],
  generates: ["generates", "generated from"],
  binds: ["binds to", "bound from"],
  "depends-on": ["depends on", "needed by"],
  tests: ["tests", "tested by"],
  "co-changes-with": ["changes together with", "changes together with"],
};

const CHIP_NAMES = {
  calls: "calls",
  references: "references",
  extends: "extends",
  implements: "implements",
  overrides: "overrides",
  imports: "imports",
  "type-uses": "type use",
  actualizes: "expect/actual",
  generates: "generates",
  binds: "contracts",
  "depends-on": "dependencies",
  tests: "tests",
  "co-changes-with": "changed together",
};

const NODE_KINDS = {
  workspace: "Workspace",
  repository: "Repository",
  module: "Module",
  "source-set": "Source set",
  directory: "Directory",
  libraries: "Libraries outside the workspace",
  "library-group": "Library group",
  library: "Library",
};

export const relationPhrase = (kind, incoming) => (RELATIONS[kind] ?? [kind, kind])[incoming ? 1 : 0];

export const chipName = (kind) => CHIP_NAMES[kind] ?? kind;

export function nodeKindName(node) {
  if (node.type === "symbol") return node.symbol.kind;
  if (node.type !== "file") return NODE_KINDS[node.type] ?? node.type;
  const kind = node.artifactType ?? "file";
  return node.generated ? `Generated ${kind}` : kind;
}

export const plural = (count, one, many = `${one}s`) => `${count.toLocaleString()} ${count === 1 ? one : many}`;

export function nodeFacts(node) {
  const facts = [];
  if (node.files > 1) facts.push(plural(node.files, "file"));
  if (node.lines > 0) facts.push(plural(node.lines, "line"));
  if (node.symbols > 0) facts.push(plural(node.symbols, "symbol"));
  if (node.commits > 0) facts.push(plural(node.commits, "commit"));
  return facts.join(", ");
}

const elementProps = new Set(["class", "text", "title", "hidden", "type"]);

export function el(tag, props = {}, ...children) {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(props)) {
    if (name === "class") element.className = value;
    else if (name === "text") element.textContent = value;
    else if (name === "onclick") element.addEventListener("click", value);
    else if (elementProps.has(name)) element[name] = value;
    else element.setAttribute(name, value);
  }
  element.append(...children.filter(Boolean));
  return element;
}
