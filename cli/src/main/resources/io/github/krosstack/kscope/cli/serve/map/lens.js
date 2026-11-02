const PLAIN = { opacity: 1, colour: null, ring: null };
const DIMMED = { opacity: 0.16, colour: null, ring: null };
const REVEAL_STEP_MILLIS = 420;

// A lens says how a bubble looks while a question is on the map: what a change affects, what a task touches or a path.
export class Lens {
  constructor(app) {
    this.app = app;
    this.current = null;
    this.startedAt = 0;
  }

  get active() {
    return this.current !== null;
  }

  clear() {
    this.current = null;
    this.app.requestFrame();
  }

  showImpact(root, impact) {
    const model = this.app.model;
    const hits = new Map();
    for (const entry of impact.affected) {
      const node = model.nodes[entry.node];
      const known = hits.get(node);
      if (node && (known === undefined || entry.distance < known)) hits.set(node, entry.distance);
    }
    const tests = new Set((impact.tests ?? []).map((index) => model.nodes[index]).filter(Boolean));
    this.#set({ kind: "impact", root, hits, tests, result: impact, holders: holdersOf(hits.keys()) });
  }

  showTask(task) {
    const model = this.app.model;
    const top = Math.max(0.0001, ...task.files.map((file) => file.score));
    const hits = new Map();
    for (const file of task.files) {
      const node = model.nodes[file.node];
      if (node) hits.set(node, file.score / top);
    }
    const tests = new Set(task.tests.map((file) => model.nodes[file.node]).filter(Boolean));
    this.#set({ kind: "task", hits, tests, result: task, holders: holdersOf([...hits.keys(), ...tests]) });
  }

  showTrace(path) {
    const hits = new Map(path.map((step, i) => [step.node, i]));
    this.#set({ kind: "trace", hits, tests: new Set(), result: path, holders: holdersOf(hits.keys()) });
  }

  #set(lens) {
    this.current = lens;
    this.startedAt = performance.now();
    this.app.requestFrame();
  }

  lookOf(node, links, selected, model) {
    const lens = this.current;
    if (!lens) return focusLook(node, links, selected, model, this.app);
    const palette = this.app.palette;
    if (node === lens.root) return { opacity: 1, colour: palette.outgoing, ring: { colour: palette.ink } };
    if (lens.tests.has(node)) return { opacity: 1, colour: palette.incoming, ring: { colour: palette.incoming } };
    if (lens.hits.has(node)) return this.#hitLook(node, lens, palette);
    if (lens.holders.has(node) || node === model.root) return { opacity: 0.8, colour: null, ring: null };
    return DIMMED;
  }

  #hitLook(node, lens, palette) {
    const value = lens.hits.get(node);
    if (lens.kind === "trace") return { opacity: 1, colour: palette.outgoing, ring: { colour: palette.ink } };
    if (lens.kind === "task") {
      const heat = palette.heat[Math.min(3, Math.floor(value * 4))];
      return { opacity: 0.45 + value * 0.55, colour: heat, ring: value > 0.6 ? { colour: heat, width: 2.5 } : null };
    }
    const reveal = (performance.now() - this.startedAt) / REVEAL_STEP_MILLIS - (value - 1);
    if (reveal < 1) this.app.requestFrame();
    const shown = this.app.camera.motionAllowed ? Math.max(0, Math.min(1, reveal)) : 1;
    const heat = palette.heat[Math.max(0, 3 - Math.min(3, value - 1))];
    return shown === 0 ? DIMMED : { opacity: 0.16 + shown * 0.84, colour: heat, ring: null };
  }
}

function focusLook(node, links, selected, model, app) {
  if (selected === node && node !== model.root) return { opacity: 1, colour: null, ring: { colour: app.palette.ink } };
  if (!selected || selected === model.root || !app.filters.showLinks) return PLAIN;
  if (node.children.length > 0 && app.scene.isOpen(node)) return PLAIN;
  if (isInside(node, selected) || links.lit.has(node)) return PLAIN;
  return { opacity: 0.32, colour: null, ring: null };
}

function isInside(node, selected) {
  for (let current = node; current; current = current.parent) if (current === selected) return true;
  return false;
}

function holdersOf(nodes) {
  const holders = new Set();
  for (const node of nodes) for (let up = node.parent; up && !holders.has(up); up = up.parent) holders.add(up);
  return holders;
}

// Breadth-first over the connections as they are filtered now, leaving the filter out only when nothing else connects.
export function tracePath(model, filters, start, goal) {
  const route = search(model, filters, start, goal, true);
  return route.length > 0 ? route : search(model, filters, start, goal, false);
}

function search(model, filters, start, goal, forwardOnly) {
  const inGoal = (node) => model.contains(goal, node);
  const adjacency = adjacencyOf(model, filters, forwardOnly);
  const origin = mapNodeOf(start);
  const cameFrom = new Map();
  const queue = [];
  for (const seed of partsOf(origin)) {
    cameFrom.set(seed, null);
    queue.push(seed);
  }
  for (let head = 0; head < queue.length; head++) {
    const node = queue[head];
    if (inGoal(node) && !model.contains(origin, node)) return walkBack(cameFrom, node);
    for (const link of adjacency.get(node) ?? []) {
      if (cameFrom.has(link.to)) continue;
      cameFrom.set(link.to, { previous: node, kind: link.kind, reversed: link.reversed });
      queue.push(link.to);
    }
  }
  return [];
}

function adjacencyOf(model, filters, forwardOnly) {
  const adjacency = new Map();
  const add = (from, link) => {
    const list = adjacency.get(from);
    if (list) list.push(link);
    else adjacency.set(from, [link]);
  };
  model.forEachEdge((from, to, kind, certainty) => {
    if (!filters.kindOn(kind) || !filters.certaintyOn(certainty)) return;
    add(from, { to, kind: model.kinds[kind], reversed: false });
    if (!forwardOnly) add(to, { to: from, kind: model.kinds[kind], reversed: true });
  });
  return adjacency;
}

// Connections join files and also modules, source sets and libraries, so a search starts from every level of a node.
function partsOf(node) {
  if (node.children.length === 0 || node.children[0].type === "symbol") return [node];
  return [node, ...node.children.flatMap(partsOf)];
}

// The overview's connections end at files, so a symbol searches from the file that declares it.
function mapNodeOf(node) {
  let current = node;
  while (current.type === "symbol") current = current.parent;
  return current;
}

function walkBack(cameFrom, end) {
  const steps = [];
  for (let node = end; node;) {
    const step = cameFrom.get(node);
    steps.unshift({ node, kind: step?.kind ?? null, reversed: step?.reversed ?? false });
    node = step?.previous ?? null;
  }
  return steps;
}
