import { edgeText } from "./diagram-words.js";

const BEND = 16;
const LOOP_REACH = 24;
const LOOP_LIFT = 14;
const LOOP_SPREAD = 6;
const STEPS = 24;
const STROKE_PAD = 6;
const LABEL_CHAR_WIDTH = 7;
const LABEL_HALF_HEIGHT = 8;

const compare = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

const direction = (edge) => (edge.from <= edge.to ? 0 : 1);

const inOrder = (a, b) => direction(a) - direction(b)
  || compare(a.label, b.label)
  || compare(a.position ?? 0, b.position ?? 0);

const slot = (index) => (index % 2 === 0 ? 1 : -1) * BEND * (Math.floor(index / 2) + 1);

// Every edge of one pair of nodes, in either direction, gets its own bend, so no two of them share a path or a label.
export function bends(edges) {
  const groups = new Map();
  for (const edge of edges) {
    const key = edge.from < edge.to ? `${edge.from}\n${edge.to}` : `${edge.to}\n${edge.from}`;
    groups.set(key, [...(groups.get(key) ?? []), edge]);
  }
  const offsets = new Map();
  for (const group of groups.values()) {
    group.sort(inOrder).forEach((edge, index) => offsets.set(edge, group.length === 1 ? 0 : slot(index)));
  }
  return offsets;
}

function shape(start, first, second, end) {
  const d = `M${start.x} ${start.y} C${first.x} ${first.y} ${second.x} ${second.y} ${end.x} ${end.y}`;
  const middle = {
    x: (start.x + 3 * first.x + 3 * second.x + end.x) / 8,
    y: (start.y + 3 * first.y + 3 * second.y + end.y) / 8,
  };
  return { d, middle, points: [start, first, second, end] };
}

export function curve(from, to, bend = 0) {
  const forward = from.x + from.w <= to.x;
  const backward = to.x + to.w <= from.x;
  const start = { x: backward ? from.x : from.x + from.w, y: from.y + from.h / 2 };
  const end = { x: forward ? to.x : to.x + to.w, y: to.y + to.h / 2 };
  const pull = forward || backward ? Math.max(40, Math.abs(end.x - start.x) / 2) : 44;
  const first = { x: start.x + (backward ? -pull : pull), y: start.y + bend };
  const second = { x: end.x + (forward ? -pull : pull), y: end.y + bend };
  return shape(start, first, second, end);
}

function loop(rect, bend) {
  const side = bend < 0 ? -1 : 1;
  const x = side > 0 ? rect.x + rect.w : rect.x;
  const centre = rect.y + rect.h / 2;
  const reach = x + side * (LOOP_REACH + Math.abs(bend));
  const lift = LOOP_SPREAD + LOOP_LIFT + Math.abs(bend) / 2;
  return shape(
    { x, y: centre - LOOP_SPREAD },
    { x: reach, y: centre - lift },
    { x: reach, y: centre + lift },
    { x, y: centre + LOOP_SPREAD },
  );
}

function pointAt(points, t) {
  const rest = 1 - t;
  const weights = [rest ** 3, 3 * rest ** 2 * t, 3 * rest * t ** 2, t ** 3];
  return {
    x: points.reduce((sum, point, index) => sum + point.x * weights[index], 0),
    y: points.reduce((sum, point, index) => sum + point.y * weights[index], 0),
  };
}

function enclose(box, x, y) {
  box.minX = Math.min(box.minX, x);
  box.maxX = Math.max(box.maxX, x);
  box.minY = Math.min(box.minY, y);
  box.maxY = Math.max(box.maxY, y);
}

function growFor(box, route, text) {
  for (let step = 0; step <= STEPS; step++) {
    const point = pointAt(route.points, step / STEPS);
    enclose(box, point.x - STROKE_PAD, point.y - STROKE_PAD);
    enclose(box, point.x + STROKE_PAD, point.y + STROKE_PAD);
  }
  const half = (text.length * LABEL_CHAR_WIDTH) / 2 + 3;
  enclose(box, route.middle.x - half, route.middle.y - LABEL_HALF_HEIGHT);
  enclose(box, route.middle.x + half, route.middle.y + LABEL_HALF_HEIGHT);
}

// The route of every edge that has both ends placed, and the box that holds the layout, every path and every label.
export function routeEdges(diagram, place) {
  const offsets = bends(diagram.edges);
  const box = { minX: 0, minY: 0, maxX: place.width, maxY: place.height };
  const routes = new Map();
  for (const edge of diagram.edges) {
    const from = place.nodes.get(edge.from);
    const to = place.nodes.get(edge.to);
    if (!from || !to) continue;
    const bend = offsets.get(edge);
    const route = edge.from === edge.to ? loop(from, bend) : curve(from, to, bend);
    routes.set(edge, route);
    growFor(box, route, edgeText(edge));
  }
  const x = Math.floor(box.minX);
  const y = Math.floor(box.minY);
  return { routes, box: { x, y, width: Math.ceil(box.maxX) - x, height: Math.ceil(box.maxY) - y } };
}
