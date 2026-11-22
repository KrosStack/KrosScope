import { layout, shown } from "./diagram-layout.js";
import { routeEdges } from "./diagram-routes.js";
import { qualifiedName, shownName } from "./diagram-names.js";
import { edgeHover, edgeText, isInheritance, stateClass } from "./diagram-words.js";

const NAMESPACE = "http://www.w3.org/2000/svg";
const ARROW = "kscope-arrow";

function svg(tag, attributes = {}, ...children) {
  const element = document.createElementNS(NAMESPACE, tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
  element.append(...children.filter(Boolean));
  return element;
}

const words = (text) => {
  const title = svg("title");
  title.textContent = text;
  return title;
};

const label = (attributes, text) => {
  const element = svg("text", attributes);
  element.textContent = text;
  return element;
};

function arrowhead() {
  const marker = svg("marker", { id: ARROW, viewBox: "0 0 10 10", refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: "auto" });
  marker.append(svg("path", { class: "arrow", d: "M0 0 L10 5 L0 10 z" }));
  return svg("defs", {}, marker);
}

function frame(box) {
  return svg(
    "g",
    { class: "group" },
    svg("rect", { x: box.x, y: box.y, width: box.w, height: box.h, rx: 7 }),
    box.caption ? label({ class: "caption", x: box.x + 8, y: box.y + 13 }, shown(box.caption)) : null,
    box.caption ? words(box.caption) : null,
  );
}

function line(edge, route) {
  const { d, middle } = route;
  const classes = ["edge", stateClass(edge.weakest), isInheritance(edge) ? "inherit" : ""].filter(Boolean).join(" ");
  const text = edgeText(edge);
  return svg(
    "g",
    { class: classes },
    svg("path", { class: "line", d, "marker-end": `url(#${ARROW})` }),
    svg("path", { class: "hit", d }),
    text ? label({ class: "edge-label", x: middle.x, y: middle.y + 4, "text-anchor": "middle" }, text) : null,
    words(edgeHover(edge)),
  );
}

function reasonBox(rect) {
  const text = `${rect.reason.reason} (${rect.reason.count})`;
  return svg(
    "g",
    { class: "node reason" },
    svg("rect", { x: rect.x, y: rect.y, width: rect.w, height: rect.h, rx: 5 }),
    label({ x: rect.x + 10, y: rect.y + rect.h / 2 + 4 }, shown(text)),
    words(`${rect.reason.reason}: ${rect.reason.count} relations that the index could not resolve or did not analyse`),
  );
}

function nodeBox(rect, files, onSelect) {
  const { node } = rect;
  const full = qualifiedName(node);
  const tie = node.symbolId && files?.[node.symbolId] ? { id: node.symbolId, file: files[node.symbolId] } : null;
  const clickable = Boolean(tie) && !node.selected;
  const attributes = { class: ["node", node.selected ? "selected" : "", clickable ? "clickable" : ""].filter(Boolean).join(" ") };
  if (clickable) Object.assign(attributes, { tabindex: 0, role: "button", "aria-label": `Show the diagram of ${full}` });
  else attributes["aria-label"] = full;
  if (node.selected) Object.assign(attributes, { tabindex: -1, "aria-current": "true" });
  const box = svg(
    "g",
    attributes,
    svg("rect", { x: rect.x, y: rect.y, width: rect.w, height: rect.h, rx: 5 }),
    label({ x: rect.x + 10, y: rect.y + rect.h / 2 + 4 }, shownName(node)),
    words(`${full}\n${node.kind}${tie || node.selected ? "" : "\nnot tied to one symbol, so it cannot be selected"}`),
  );
  if (clickable) {
    box.addEventListener("click", () => onSelect(tie, false));
    box.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      onSelect(tie, true);
    });
  }
  return box;
}

// The drawing of one answer of the server. `onSelect` gets the symbol id and the file node of a node that the user picks, and whether the keyboard picked it.
export function drawDiagram(answer, onSelect) {
  const { diagram, files } = answer;
  const place = layout(diagram);
  const { routes, box } = routeEdges(diagram, place);
  const edges = diagram.edges.flatMap((edge) => (routes.has(edge) ? [line(edge, routes.get(edge))] : []));
  const nodes = diagram.nodes.map((node) => nodeBox(place.nodes.get(node.id), files, onSelect));
  const reasons = (diagram.reasons ?? []).map((reason) => reasonBox(place.nodes.get(reason.id)));
  return svg(
    "svg",
    { viewBox: `${box.x} ${box.y} ${box.width} ${box.height}`, width: box.width, height: box.height, role: "group", "aria-label": diagram.title },
    arrowhead(),
    ...place.frames.map(frame),
    ...edges,
    ...nodes,
    ...reasons,
  );
}
