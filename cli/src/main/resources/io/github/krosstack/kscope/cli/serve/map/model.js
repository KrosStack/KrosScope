import { enclosing, packSiblings } from "./pack.js";

const STRIDE = 5;
const CONTAINER_PAD = 0.07;

export class MapModel {
  constructor(overview) {
    this.overview = overview;
    this.version = overview.mapVersion;
    this.kinds = overview.kinds ?? [];
    this.certainties = overview.certainties ?? ["exact", "probable", "heuristic"];
    this.edges = overview.edges ?? [];
    this.nodes = overview.nodes.map(toNode);
    this.byId = new Map(this.nodes.map((node) => [node.id, node]));
    this.root = this.nodes[0] ?? null;
    if (!this.root) return;
    this.#link();
    this.#countEdges();
    this.#rollUp();
    layoutNode(this.root);
    placeChildren(this.root);
  }

  get edgeCount() {
    return this.edges.length / STRIDE;
  }

  contains(ancestor, node) {
    return node.tin >= ancestor.tin && node.tin <= ancestor.tout;
  }

  // The child of the lowest common ancestor that holds `other`: the level at which a list of connections reads best.
  neighbourLevel(selected, other) {
    let node = other;
    while (node.parent && !this.contains(node.parent, selected)) node = node.parent;
    return node;
  }

  forEachEdge(visit) {
    const { edges, nodes } = this;
    for (let i = 0; i < edges.length; i += STRIDE) {
      visit(nodes[edges[i]], nodes[edges[i + 1]], edges[i + 2], edges[i + 3], edges[i + 4]);
    }
  }

  ancestors(node) {
    const chain = [];
    for (let current = node; current; current = current.parent) chain.unshift(current);
    return chain;
  }

  addSymbols(file, symbols) {
    const nodes = new Map();
    file.children = [];
    file.symbolIndex = nodes;
    for (const symbol of symbols) {
      nodes.set(symbol.id, {
        idx: this.nodes.length + nextSymbolIndex(), id: `y:${symbol.id}`, type: "symbol", label: symbol.name,
        sid: symbol.id, symbol, parent: file, children: [], depth: file.depth + 1,
        tin: file.tin, tout: file.tin, lines: 1, symbols: 0, files: 0, unresolved: 0, notAnalysed: 0, commits: 0,
        incoming: 0, outgoing: 0, x: file.x, y: file.y, r: 0,
      });
    }
    for (const node of nodes.values()) {
      this.byId.set(node.id, node);
      const holder = nodes.get(node.symbol.container);
      node.parent = holder ?? file;
      node.parent.children.push(node);
    }
    for (const child of file.children) {
      setDepths(child, file.depth + 1);
      layoutSymbol(child);
    }
    fitInside(file);
    placeChildren(file);
    return nodes;
  }

  #link() {
    let clock = 0;
    for (const node of this.nodes) {
      if (node.parentIndex >= 0) {
        node.parent = this.nodes[node.parentIndex];
        node.parent.children.push(node);
        node.depth = node.parent.depth + 1;
      }
    }
    const visit = (node) => {
      node.tin = clock++;
      for (const child of node.children) visit(child);
      node.tout = clock - 1;
    };
    visit(this.root);
  }

  #countEdges() {
    this.forEachEdge((from, to, _kind, _certainty, count) => {
      from.outgoing += count;
      to.incoming += count;
    });
  }

  #rollUp() {
    for (let i = this.nodes.length - 1; i > 0; i--) {
      const node = this.nodes[i];
      const parent = node.parent;
      if (node.type === "file") node.files = 1;
      parent.files += node.files;
      parent.lines += node.lines;
      parent.symbols += node.symbols;
      parent.unresolved += node.unresolved;
      parent.notAnalysed += node.notAnalysed;
      parent.commits += node.commits;
      parent.incoming += node.incoming;
      parent.outgoing += node.outgoing;
    }
  }
}

let symbolIndex = 0;

function nextSymbolIndex() {
  symbolIndex += 1;
  return symbolIndex;
}

function toNode(raw, idx) {
  return {
    idx,
    id: raw.id,
    type: raw.type,
    label: raw.label === ":" ? "(root project)" : raw.label,
    path: raw.path ?? null,
    artifactType: raw.artifactType ?? null,
    category: raw.category ?? null,
    generated: raw.generated ?? false,
    hash: raw.hash ?? null,
    traits: raw.traits ?? null,
    platform: raw.platform ?? null,
    parentIndex: raw.parent,
    parent: null,
    children: [],
    depth: 0,
    tin: 0,
    tout: 0,
    lines: raw.lines ?? 0,
    symbols: raw.symbols ?? 0,
    files: 0,
    commits: raw.commits ?? 0,
    unresolved: raw.unresolved ?? 0,
    notAnalysed: raw.notAnalysed ?? 0,
    incoming: 0,
    outgoing: 0,
    x: 0,
    y: 0,
    r: 0,
    rx: 0,
    ry: 0,
  };
}

function leafRadius(node) {
  if (node.type === "library") return 2.2 + Math.sqrt(node.incoming) * 0.7;
  const mass = Math.max(node.lines, 12) + node.symbols * 5;
  return 1.6 + Math.sqrt(mass) * 0.55;
}

function layoutNode(node) {
  if (node.children.length === 0) {
    node.r = leafRadius(node);
    return;
  }
  for (const child of node.children) layoutNode(child);
  packChildren(node, 0.012);
}

function setDepths(node, depth) {
  node.depth = depth;
  for (const child of node.children) setDepths(child, depth + 1);
}

function layoutSymbol(node) {
  if (node.children.length === 0) {
    node.r = 1 + Math.sqrt(Math.max(1, (node.symbol.endLine ?? 0) - (node.symbol.line ?? 0))) * 0.6;
    return;
  }
  for (const child of node.children) layoutSymbol(child);
  packChildren(node, 0.03);
}

function packChildren(node, gapShare) {
  const sorted = [...node.children].sort((a, b) => b.r - a.r || (a.id < b.id ? -1 : 1));
  const largest = sorted[0].r;
  const gap = Math.max(0.35, largest * gapShare);
  const circles = sorted.map((child) => ({ x: 0, y: 0, r: child.r + gap, child }));
  packSiblings(circles);
  const around = enclosing(circles);
  for (const circle of circles) {
    circle.child.rx = circle.x - around.x;
    circle.child.ry = circle.y - around.y;
  }
  node.r = around.r * (1 + CONTAINER_PAD) + 0.6;
}

// A file keeps the size it has on the map, so its symbols scale to fit it and the map around it does not move.
function fitInside(file) {
  const fixedRadius = file.r;
  packChildren(file, 0.03);
  const scale = (fixedRadius / file.r) * 0.96;
  file.r = fixedRadius;
  for (const child of file.children) scaleBranch(child, scale);
}

function scaleBranch(node, scale) {
  node.r *= scale;
  node.rx *= scale;
  node.ry *= scale;
  for (const child of node.children) scaleBranch(child, scale);
}

export function placeChildren(node) {
  for (const child of node.children) {
    child.x = node.x + child.rx;
    child.y = node.y + child.ry;
    placeChildren(child);
  }
}
