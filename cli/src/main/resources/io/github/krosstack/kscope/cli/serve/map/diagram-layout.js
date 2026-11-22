import { shownName } from "./diagram-names.js";

const NODE_HEIGHT = 26;
const NODE_GAP = 8;
const BOX_PAD = 8;
const CAPTION_HEIGHT = 16;
const BOX_GAP = 12;
const LAYER_GAP = 90;
const MARGIN = 14;
const CHAR_WIDTH = 7;
const CAPTION_CHAR_WIDTH = 6.6;
const MIN_NODE_WIDTH = 96;
const MAX_SHOWN = 36;

export const shown = (label) => (label.length > MAX_SHOWN ? `${label.slice(0, MAX_SHOWN - 1)}…` : label);

function push(map, key, value) {
  if (map.has(key)) map.get(key).push(value);
  else map.set(key, [value]);
}

function distances(start, next) {
  const found = new Map([[start, 0]]);
  const queue = [start];
  for (let head = 0; head < queue.length; head++) {
    const here = queue[head];
    for (const other of next.get(here) ?? []) {
      if (found.has(other)) continue;
      found.set(other, found.get(here) + 1);
      queue.push(other);
    }
  }
  return found;
}

function placeStrays(edges, layers) {
  for (let changed = true; changed;) {
    changed = false;
    for (const { from, to } of edges) {
      if (layers.has(from) === layers.has(to)) continue;
      if (layers.has(from)) layers.set(to, layers.get(from) + 1);
      else layers.set(from, layers.get(to) - 1);
      changed = true;
    }
  }
}

// Callers lie left of the selected symbol and what it uses right of it. A node on both sides at the same distance
// is drawn on the right, and a node that the walk reached by neither way takes its place from a neighbour.
export function assignLayers(nodes, edges) {
  const ids = new Set(nodes.map((node) => node.id));
  const joins = edges.filter((edge) => ids.has(edge.from) && ids.has(edge.to) && edge.from !== edge.to);
  const uses = new Map();
  const usedBy = new Map();
  for (const { from, to } of joins) {
    push(uses, from, to);
    push(usedBy, to, from);
  }
  const selected = nodes.find((node) => node.selected) ?? nodes[0];
  const right = distances(selected.id, uses);
  const left = distances(selected.id, usedBy);
  const layers = new Map();
  for (const { id } of nodes) {
    if (right.has(id) && (!left.has(id) || right.get(id) <= left.get(id))) layers.set(id, right.get(id));
    else if (left.has(id)) layers.set(id, -left.get(id));
  }
  placeStrays(joins, layers);
  for (const { id } of nodes) if (!layers.has(id)) layers.set(id, 1);
  return layers;
}

const byLabel = (a, b) => a.label.localeCompare(b.label) || a.order - b.order;

function boxesOf(members, captions) {
  const groups = new Map();
  for (const member of members) push(groups, member.node.group ?? "", member);
  return [...groups.entries()]
    .map(([group, nodes]) => ({ caption: captions.get(group) ?? "", nodes: nodes.sort(byLabel) }))
    .sort((a, b) => a.caption.localeCompare(b.caption));
}

function columnsOf(diagram, layers) {
  const captions = new Map(diagram.groups.map((group) => [group.id, group.label]));
  const byLayer = new Map();
  diagram.nodes.forEach((node, order) => push(byLayer, layers.get(node.id), { node, label: node.label, order }));
  return [...byLayer.keys()].sort((a, b) => a - b).map((layer) => ({ layer, boxes: boxesOf(byLayer.get(layer), captions) }));
}

const nodeWidth = (node) => Math.max(MIN_NODE_WIDTH, shownName(node).length * CHAR_WIDTH + 20);

function boxHeight(box) {
  const rows = box.nodes.length * NODE_HEIGHT + (box.nodes.length - 1) * NODE_GAP;
  return rows + 2 * BOX_PAD + (box.caption ? CAPTION_HEIGHT : 0);
}

const columnHeight = (boxes) => boxes.reduce((sum, box) => sum + boxHeight(box), 0) + (boxes.length - 1) * BOX_GAP;

function boxWidth(box) {
  const captionWidth = box.caption ? shown(box.caption).length * CAPTION_CHAR_WIDTH + 2 * BOX_PAD : 0;
  return Math.max(captionWidth - 2 * BOX_PAD, ...box.nodes.map((member) => nodeWidth(member.node)));
}

function placeColumn(column, left, top) {
  const width = Math.max(...column.boxes.map(boxWidth)) + 2 * BOX_PAD;
  const rects = [];
  const frames = [];
  let y = top;
  for (const box of column.boxes) {
    const height = boxHeight(box);
    frames.push({ x: left, y, w: width, h: height, caption: box.caption });
    let rowY = y + BOX_PAD + (box.caption ? CAPTION_HEIGHT : 0);
    for (const member of box.nodes) {
      rects.push({ node: member.node, x: left + BOX_PAD, y: rowY, w: width - 2 * BOX_PAD, h: NODE_HEIGHT });
      rowY += NODE_HEIGHT + NODE_GAP;
    }
    y += height + BOX_GAP;
  }
  return { width, rects, frames };
}

function reasonRects(reasons, left, top) {
  const width = Math.max(MIN_NODE_WIDTH, ...reasons.map((reason) => shown(`${reason.reason} (${reason.count})`).length * CHAR_WIDTH + 20));
  const rects = reasons.map((reason, index) => ({
    reason, x: left, y: top + index * (NODE_HEIGHT + NODE_GAP), w: width, h: NODE_HEIGHT,
  }));
  return { width, rects };
}

// Where every node, group frame and reason node goes. Inside a layer the groups follow each other by caption and the
// nodes of a group by label, so that one diagram is always drawn the same way.
export function layout(diagram) {
  const layers = assignLayers(diagram.nodes, diagram.edges);
  const columns = columnsOf(diagram, layers);
  const reasons = diagram.reasons ?? [];
  const reasonHeight = reasons.length ? reasons.length * (NODE_HEIGHT + NODE_GAP) - NODE_GAP : 0;
  const tallest = Math.max(reasonHeight, ...columns.map((column) => columnHeight(column.boxes)));
  const nodes = new Map();
  const frames = [];
  let left = MARGIN;
  for (const column of columns) {
    const placed = placeColumn(column, left, MARGIN + (tallest - columnHeight(column.boxes)) / 2);
    placed.rects.forEach((rect) => nodes.set(rect.node.id, rect));
    frames.push(...placed.frames);
    left += placed.width + LAYER_GAP;
  }
  const reasonColumn = reasonRects(reasons, left, MARGIN + (tallest - reasonHeight) / 2);
  reasonColumn.rects.forEach((rect) => nodes.set(rect.reason.id, rect));
  const right = reasons.length ? left + reasonColumn.width : left - LAYER_GAP;
  return { width: right + MARGIN, height: tallest + 2 * MARGIN, nodes, frames, layers };
}
