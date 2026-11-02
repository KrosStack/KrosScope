import { symbolColour } from "./palette.js";

const OPEN_RADIUS = 30;
const FADE_SPAN = 34;
const SMALLEST_DRAWN = 0.8;
const ARC_LABEL_RADIUS = 44;
const LEAF_LABEL_RADIUS = 17;
const MOST_ARC_LABELS = 60;
const PULSE_MILLIS = 3200;
const SERIF = '"Iowan Old Style", "Palatino Linotype", Palatino, "Book Antiqua", Georgia, serif';
const MONO = 'ui-monospace, "Cascadia Code", "SF Mono", Menlo, Consolas, monospace';

export class Scene {
  constructor(app, ctx) {
    this.app = app;
    this.ctx = ctx;
  }

  isOpen(node) {
    return node.children.length > 0 && node.r * this.app.camera.k > OPEN_RADIUS;
  }

  // What each node of the map appears as at this zoom: itself, or its deepest ancestor that is drawn closed.
  representatives() {
    const { model, camera } = this.app;
    const result = new Map();
    const assign = (node, shown) => {
      result.set(node, shown);
      for (const child of node.children) if (child.type !== "symbol") assign(child, shown);
    };
    const walk = (node) => {
      const opened = this.isOpen(node) && node.children[0].type !== "symbol" && camera.visible(node);
      if (!opened) {
        assign(node, node);
        return;
      }
      result.set(node, node);
      for (const child of node.children) walk(child);
    };
    walk(model.root);
    return result;
  }

  draw(palette, now) {
    const { model } = this.app;
    this.labels = [];
    this.#drawNode(model.root, palette, 1, now);
  }

  #drawNode(node, palette, parentFade, now) {
    const { camera } = this.app;
    const screenRadius = node.r * camera.k;
    if (screenRadius < SMALLEST_DRAWN || !camera.visible(node)) return;
    this.#paintBubble(node, screenRadius, palette, parentFade, now);
    if (!this.isOpen(node)) {
      this.#queueLeafLabel(node, screenRadius, parentFade);
      return;
    }
    const fade = Math.min(1, (screenRadius - OPEN_RADIUS) / FADE_SPAN) * parentFade;
    this.#queueArcLabel(node, screenRadius, parentFade);
    for (const child of node.children) this.#drawNode(child, palette, fade, now);
  }

  #paintBubble(node, radius, palette, fade, now) {
    const { ctx, app } = this;
    const { camera, lens } = app;
    const x = camera.toScreenX(node.x);
    const y = camera.toScreenY(node.y);
    const leaf = !this.isOpen(node);
    const colour = node.type === "symbol" ? symbolColour(node.symbol, palette) : node.colour;
    const look = lens.lookOf(node, app.links, app.selected, app.model);
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.globalAlpha = fade * (leaf ? 0.62 : 0.075) * look.opacity;
    ctx.fillStyle = look.colour ?? colour;
    ctx.fill();
    ctx.globalAlpha = fade * (leaf ? 0.8 : 0.55) * Math.max(look.opacity, 0.5);
    ctx.lineWidth = leaf ? 0.8 : 1 + Math.min(1.6, node.children.length / 40);
    ctx.strokeStyle = look.colour ?? colour;
    ctx.stroke();
    if (look.ring) this.#ring(x, y, radius, look.ring, palette);
    this.#pulse(node, x, y, radius, palette, now);
    ctx.globalAlpha = 1;
  }

  #ring(x, y, radius, ring, palette) {
    const { ctx } = this;
    ctx.globalAlpha = 1;
    ctx.lineWidth = ring.width ?? 2;
    ctx.strokeStyle = ring.colour ?? palette.ink;
    ctx.beginPath();
    ctx.arc(x, y, radius + 2, 0, Math.PI * 2);
    ctx.stroke();
  }

  #pulse(node, x, y, radius, palette, now) {
    if (!node.pulseAt) return;
    const age = now - node.pulseAt;
    if (age > PULSE_MILLIS) {
      node.pulseAt = 0;
      return;
    }
    this.app.requestFrame();
    if (age < 0) return;
    const { ctx } = this;
    const t = age / PULSE_MILLIS;
    ctx.globalAlpha = (1 - t) * 0.9;
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = palette[node.pulseKind] ?? palette.changed;
    ctx.beginPath();
    ctx.arc(x, y, Math.max(radius, 5) + 3 + t * 26, 0, Math.PI * 2);
    ctx.stroke();
    if (this.isOpen(node)) return;
    ctx.globalAlpha = (1 - t) * 0.6;
    ctx.fillStyle = ctx.strokeStyle;
    ctx.beginPath();
    ctx.arc(x, y, Math.max(radius, 3), 0, Math.PI * 2);
    ctx.fill();
  }

  #queueArcLabel(node, radius, fade) {
    if (radius < ARC_LABEL_RADIUS) return;
    this.labels.push({ node, radius, fade, arc: true });
  }

  #queueLeafLabel(node, radius, fade) {
    if (radius < LEAF_LABEL_RADIUS) return;
    this.labels.push({ node, radius, fade, arc: false });
  }

  drawLabels(palette) {
    const arcs = this.labels.filter((label) => label.arc).sort((a, b) => b.radius - a.radius);
    for (const label of arcs.slice(0, MOST_ARC_LABELS)) this.#arcLabel(label, palette);
    for (const label of this.labels) if (!label.arc) this.#leafLabel(label, palette);
  }

  #arcLabel({ node, radius, fade }, palette) {
    const { ctx, app } = this;
    const { camera } = app;
    const size = Math.max(11, Math.min(radius * 0.085, 24));
    ctx.font = `italic ${size}px ${SERIF}`;
    const text = shorten(ctx, node.label, radius * 1.7);
    if (!text) return;
    const widths = [...text].map((char) => ctx.measureText(char).width);
    const total = widths.reduce((sum, width) => sum + width, 0);
    const pathRadius = radius - size * 0.38 - 2;
    const x = camera.toScreenX(node.x);
    const y = camera.toScreenY(node.y);
    let angle = -Math.PI / 2 - total / pathRadius / 2;
    ctx.fillStyle = palette.ink;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    [...text].forEach((char, i) => {
      const middle = angle + widths[i] / pathRadius / 2;
      ctx.save();
      ctx.globalAlpha = fade;
      ctx.translate(x + Math.cos(middle) * pathRadius, y + Math.sin(middle) * pathRadius);
      ctx.rotate(middle + Math.PI / 2);
      ctx.lineWidth = 3;
      ctx.strokeStyle = palette.halo;
      ctx.strokeText(char, 0, 0);
      ctx.fillText(char, 0, 0);
      ctx.restore();
      angle += widths[i] / pathRadius;
    });
  }

  #leafLabel({ node, radius, fade }, palette) {
    const { ctx, app } = this;
    const { camera } = app;
    const size = Math.max(10, Math.min(radius * 0.3, 15));
    ctx.font = `${size}px ${MONO}`;
    const text = shorten(ctx, node.label, radius * 1.85);
    if (!text) return;
    const x = camera.toScreenX(node.x);
    const y = camera.toScreenY(node.y);
    ctx.globalAlpha = fade;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.lineWidth = 3;
    ctx.strokeStyle = palette.halo;
    ctx.fillStyle = palette.ink;
    ctx.strokeText(text, x, y);
    ctx.fillText(text, x, y);
    ctx.globalAlpha = 1;
  }

  nodeAt(sx, sy) {
    const { camera, model } = this.app;
    const world = camera.toWorld(sx, sy);
    let found = null;
    let node = model.root;
    while (node) {
      if (Math.hypot(world.x - node.x, world.y - node.y) > node.r) break;
      found = node;
      node = this.isOpen(node)
        ? node.children.find((child) => Math.hypot(world.x - child.x, world.y - child.y) <= child.r) ?? null
        : null;
    }
    return found;
  }
}

function shorten(ctx, label, room) {
  if (ctx.measureText(label).width <= room) return label;
  const tail = label.lastIndexOf("/");
  const short = tail > 0 ? `…${label.slice(tail)}` : label;
  if (ctx.measureText(short).width <= room) return short;
  let end = short.length - 1;
  while (end > 3 && ctx.measureText(`${short.slice(0, end)}…`).width > room) end--;
  return end > 3 ? `${short.slice(0, end)}…` : "";
}
