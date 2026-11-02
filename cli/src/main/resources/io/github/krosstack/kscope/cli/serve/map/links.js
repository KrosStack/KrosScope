const KEY_BASE = 4_000_000;
const OUTWARD = 0;
const INWARD = 1;
const AROUND = 2;
const MOST_DRAWN = 1400;
const MOST_DRAWN_AROUND_FOCUS = 260;
const STRAIGHTENING = 0.8;

export class Links {
  constructor(app) {
    this.app = app;
    this.drawn = [];
    this.lit = new Set();
    this.stale = true;
  }

  invalidate() {
    this.stale = true;
    this.app.requestFrame();
  }

  refresh() {
    if (!this.stale || !this.app.model?.root) return;
    this.stale = false;
    this.#aggregate();
  }

  // Each edge is lifted to the deepest bubble of its ends that is on screen at this zoom, then equal pairs are merged.
  #aggregate() {
    const { model, filters, scene, selected } = this.app;
    const representative = scene.representatives();
    const focus = selected && selected !== model.root ? selected : null;
    const merged = new Map();
    this.lit = new Set();
    const skipped = this.#filesWithAllLinks();
    model.forEachEdge((from, to, kind, certainty, count) => {
      if (!filters.kindOn(kind) || !filters.certaintyOn(certainty)) return;
      if (skipped.has(from) || skipped.has(to)) return;
      const a = representative.get(from);
      const b = representative.get(to);
      if (a === b) return;
      const direction = focus ? directionOf(model, focus, from, to) : AROUND;
      this.#add(merged, a, b, direction, certainty, count);
      if (direction !== AROUND) this.#light(direction === OUTWARD ? to : from);
    });
    this.#addSymbolLinks(merged, representative, focus);
    this.drawn = strongest([...merged.values()], Boolean(focus));
  }

  // A detail that hit its cap lacks some links, so that file keeps its aggregate links and draws no symbol links.
  #filesWithAllLinks() {
    return new Set([...this.app.openFiles()].filter((file) => !file.detail.truncated));
  }

  #add(merged, a, b, direction, certainty, count) {
    const key = (a.idx * KEY_BASE + b.idx) * 3 + direction;
    const entry = merged.get(key);
    if (entry) {
      entry.count += count;
      entry.certainty = Math.min(entry.certainty, certainty);
    } else {
      merged.set(key, { a, b, direction, certainty, count });
    }
  }

  #light(node) {
    for (let current = node; current && !this.lit.has(current); current = current.parent) this.lit.add(current);
  }

  #addSymbolLinks(merged, representative, focus) {
    const { filters, model } = this.app;
    const kindIndex = new Map(model.kinds.map((kind, i) => [kind, i]));
    const certaintyIndex = new Map(model.certainties.map((name, i) => [name, i]));
    const open = this.#filesWithAllLinks();
    for (const file of open) {
      const detail = file.detail;
      const symbols = file.symbolIndex;
      const end = (symbolId, nodeIndex) => symbols?.get(symbolId) ?? this.#endOf(symbolId, nodeIndex, representative);
      // A link that leaves an open file is already counted among that file's outgoing links.
      const arriving = detail.incoming.filter((link) => !open.has(model.nodes[link.fromNode]));
      for (const link of [...detail.outgoing, ...arriving]) {
        const kind = kindIndex.get(link.kind);
        const certainty = certaintyIndex.get(link.certainty) ?? 2;
        if (kind === undefined || !filters.kindOn(kind) || !filters.certaintyOn(certainty)) continue;
        const a = end(link.fromSymbol, link.fromNode);
        const b = end(link.toSymbol, link.toNode);
        if (!a || !b || a === b) continue;
        const direction = focus ? this.#symbolDirection(focus, a, b) : AROUND;
        this.#add(merged, a, b, direction, certainty, 1);
        if (direction !== AROUND) this.#light(direction === OUTWARD ? b : a);
      }
    }
  }

  #endOf(symbolId, nodeIndex, representative) {
    const node = this.app.model.nodes[nodeIndex];
    if (!node) return undefined;
    const opened = this.app.scene.isOpen(node) ? node.symbolIndex?.get(symbolId) : undefined;
    return opened ?? representative.get(node);
  }

  #symbolDirection(focus, a, b) {
    const inside = (node) => isWithin(node, focus);
    if (inside(a) && !inside(b)) return OUTWARD;
    if (inside(b) && !inside(a)) return INWARD;
    return AROUND;
  }

  draw(ctx, camera, palette, now) {
    this.refresh();
    const lens = this.app.lens.current;
    if (lens?.kind === "trace") {
      this.#drawTrace(ctx, camera, palette, lens.result, now);
      return;
    }
    if (!this.app.filters.showLinks || this.drawn.length === 0) return;
    const peak = Math.log1p(this.drawn[0].count);
    const focus = this.app.selected && this.app.selected !== this.app.model.root;
    const flowing = camera.motionAllowed && focus && !lens;
    const quiet = (lens ? 0.22 : 1) * palette.linkGain;
    ctx.save();
    ctx.globalCompositeOperation = palette.blend;
    ctx.lineCap = "round";
    for (const link of this.drawn) {
      const around = link.direction === AROUND;
      const share = Math.log1p(link.count) / (peak || 1);
      ctx.strokeStyle = around ? palette.context : link.direction === OUTWARD ? palette.outgoing : palette.incoming;
      ctx.globalAlpha = quiet * (around ? (focus ? 0.05 : 0.1 + share * 0.4) : 0.4 + share * 0.55);
      ctx.lineWidth = around ? 0.6 + share * 2 : 1 + share * 3;
      ctx.setLineDash(dashFor(link.certainty, ctx.lineWidth, around));
      ctx.lineDashOffset = flowing && !around ? -now / 40 : 0;
      this.#stroke(ctx, camera, link);
    }
    ctx.restore();
  }

  #drawTrace(ctx, camera, palette, path, now) {
    ctx.save();
    ctx.lineCap = "round";
    ctx.strokeStyle = palette.outgoing;
    ctx.lineWidth = 3;
    ctx.setLineDash([10, 6]);
    ctx.lineDashOffset = camera.motionAllowed ? -now / 40 : 0;
    for (let i = 1; i < path.length; i++) this.#stroke(ctx, camera, { a: path[i - 1].node, b: path[i].node });
    ctx.restore();
    if (camera.motionAllowed) this.app.requestFrame();
  }

  #stroke(ctx, camera, link) {
    const points = route(link.a, link.b).map((p) => ({ x: camera.toScreenX(p.x), y: camera.toScreenY(p.y) }));
    trimToMembrane(points, link.a.r * camera.k, link.b.r * camera.k);
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length - 1; i++) {
      const next = points[i + 1];
      ctx.quadraticCurveTo(points[i].x, points[i].y, (points[i].x + next.x) / 2, (points[i].y + next.y) / 2);
    }
    const last = points[points.length - 1];
    ctx.lineTo(last.x, last.y);
    ctx.stroke();
  }
}

function isWithin(node, ancestor) {
  for (let current = node; current; current = current.parent) if (current === ancestor) return true;
  return false;
}

function directionOf(model, focus, from, to) {
  if (focus.type === "symbol") return AROUND;
  const fromInside = model.contains(focus, from);
  const toInside = model.contains(focus, to);
  if (fromInside === toInside) return AROUND;
  return fromInside ? OUTWARD : INWARD;
}

function strongest(links, focused) {
  links.sort((a, b) => b.count - a.count || a.a.idx - b.a.idx || a.b.idx - b.b.idx);
  if (!focused) return links.slice(0, MOST_DRAWN);
  const direct = links.filter((link) => link.direction !== AROUND);
  const around = links.filter((link) => link.direction === AROUND).slice(0, MOST_DRAWN_AROUND_FOCUS);
  return [...around, ...direct];
}

function dashFor(certainty, width, around) {
  if (certainty === 0) return around ? [] : [width * 5, width * 2.5];
  if (certainty === 1) return [width * 3, width * 3];
  return [width, width * 3.5];
}

// The path follows the ancestors of both ends up to where they meet. Pulling it part of the way towards the straight
// line (Holten's bundling strength) keeps bundles visible without collapsing them onto one trunk.
function route(a, b) {
  const up = [];
  const down = [];
  let left = a;
  let right = b;
  while (left.depth > right.depth) { up.push(left); left = left.parent; }
  while (right.depth > left.depth) { down.push(right); right = right.parent; }
  while (left !== right) {
    up.push(left);
    down.push(right);
    left = left.parent;
    right = right.parent;
  }
  const chain = [...up, left, ...down.reverse()];
  const last = chain.length - 1;
  return chain.map((node, i) => {
    const t = i / last;
    return {
      x: node.x * STRAIGHTENING + (a.x + (b.x - a.x) * t) * (1 - STRAIGHTENING),
      y: node.y * STRAIGHTENING + (a.y + (b.y - a.y) * t) * (1 - STRAIGHTENING),
    };
  });
}

function trimToMembrane(points, startRadius, endRadius) {
  moveAlong(points[0], points[1], startRadius);
  moveAlong(points[points.length - 1], points[points.length - 2], endRadius);
}

function moveAlong(point, towards, distance) {
  const dx = towards.x - point.x;
  const dy = towards.y - point.y;
  const length = Math.hypot(dx, dy) || 1;
  const step = Math.min(distance, length * 0.9);
  point.x += (dx / length) * step;
  point.y += (dy / length) * step;
}
