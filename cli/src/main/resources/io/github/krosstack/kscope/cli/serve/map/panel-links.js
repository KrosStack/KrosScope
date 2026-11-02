import { el, plural, relationPhrase } from "./words.js";

const MOST_ROWS = 40;

export function row(direction, name, count, share, why, onclick) {
  const button = el("button", { class: `row ${direction}`, type: "button" });
  if (onclick) button.addEventListener("click", onclick);
  else button.disabled = true;
  button.append(el("span", { class: "name", text: name }), el("span", { class: "count", text: count }));
  if (why) button.append(el("span", { class: "why", text: why }));
  if (share !== null) {
    const bar = el("span", { class: "bar" });
    bar.style.width = `${Math.max(2, share * 100)}%`;
    button.append(bar);
  }
  return button;
}

export function listSection(title, note, rows, emptyText) {
  const section = [el("h2", {}, el("span", { text: title }), el("small", { text: note }))];
  if (rows.length === 0) section.push(el("p", { class: "empty", text: emptyText }));
  else section.push(el("ul", { class: "rows" }, ...rows.map((item) => el("li", {}, item))));
  return section;
}

// A container's connections, rolled up to the level of its neighbours and described by their most common relation.
export function rollUpLinks(app, node) {
  const { model, filters } = app;
  const out = new Map();
  const incoming = new Map();
  model.forEachEdge((from, to, kind, certainty, count) => {
    if (!filters.kindOn(kind) || !filters.certaintyOn(certainty)) return;
    const fromInside = model.contains(node, from);
    const toInside = model.contains(node, to);
    if (fromInside === toInside) return;
    const other = model.neighbourLevel(node, fromInside ? to : from);
    collect(fromInside ? out : incoming, other, model.kinds[kind], certainty, count);
  });
  return { out: sorted(out), incoming: sorted(incoming) };
}

function collect(groups, other, kind, certainty, count) {
  const group = groups.get(other) ?? { node: other, count: 0, kinds: new Map(), unsure: 0 };
  group.count += count;
  group.kinds.set(kind, (group.kinds.get(kind) ?? 0) + count);
  if (certainty > 0) group.unsure += count;
  groups.set(other, group);
}

const sorted = (groups) => [...groups.values()].sort((a, b) => b.count - a.count || a.node.tin - b.node.tin);

export function rollUpSections(app, node) {
  if (!node.parent) return innerSection(app, node);
  const { out, incoming } = rollUpLinks(app, node);
  return [
    ...listSection("Uses", plural(sum(out), "connection"), groupRows(app, out, "out", false), "Nothing outside it."),
    ...listSection("Used by", plural(sum(incoming), "connection"), groupRows(app, incoming, "in", true), "Nothing outside it reaches it."),
    ...innerSection(app, node),
  ];
}

function innerSection(app, node) {
  if (node.children.length < 2) return [];
  const { model, filters } = app;
  const pairs = new Map();
  model.forEachEdge((from, to, kind, certainty, count) => {
    if (!filters.kindOn(kind) || !filters.certaintyOn(certainty)) return;
    if (from === node || to === node) return;
    if (!model.contains(node, from) || !model.contains(node, to)) return;
    const a = partOf(node, from);
    const b = partOf(node, to);
    if (a === b) return;
    const key = a.idx * 4_000_000 + b.idx;
    const pair = pairs.get(key) ?? { a, b, count: 0 };
    pair.count += count;
    pairs.set(key, pair);
  });
  const sortedPairs = [...pairs.values()].sort((x, y) => y.count - x.count);
  const peak = sortedPairs[0]?.count ?? 1;
  const rows = sortedPairs.slice(0, 16).map((pair) => row(
    "",
    `${pair.a.label} uses ${pair.b.label}`,
    pair.count.toLocaleString(),
    pair.count / peak,
    null,
    () => app.select(pair.a, { fly: true }),
  ));
  return listSection("Between its parts", plural(sortedPairs.length, "pair"), rows, "Its parts do not use each other.");
}

function partOf(holder, node) {
  let part = node;
  while (part.parent !== holder) part = part.parent;
  return part;
}

const sum = (groups) => groups.reduce((total, group) => total + group.count, 0);

function groupRows(app, groups, direction, isIncoming) {
  const peak = groups[0]?.count ?? 1;
  return groups.slice(0, MOST_ROWS).map((group) => {
    const words = [...group.kinds.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([kind, count]) => `${relationPhrase(kind, isIncoming)} ${count.toLocaleString()}`)
      .join(", ");
    const unsure = group.unsure > 0 ? `, ${group.unsure.toLocaleString()} not exact` : "";
    return row(direction, group.node.label, group.count.toLocaleString(), group.count / peak, words + unsure, () => app.select(group.node, { fly: true }));
  });
}

export function linkSections(app, node, detail) {
  const keep = (link, side) => node.type !== "symbol" || link[side] === node.sid;
  const out = detail.outgoing.filter((link) => keep(link, "fromSymbol"));
  const incoming = detail.incoming.filter((link) => keep(link, "toSymbol"));
  // The server lists a link inside one file only as outgoing, but for a symbol it can also be a use from its neighbour.
  if (node.type === "symbol") {
    incoming.push(...detail.outgoing.filter((link) => link.toSymbol === node.sid && link.fromNode === link.toNode));
  }
  const open = detail.open.filter((link) => node.type !== "symbol" || link.fromSymbol === node.sid);
  return [
    ...listSection("Uses", truncationNote(out, detail), linkRows(app, out, false), "It uses nothing the index knows of."),
    ...listSection("Used by", plural(incoming.length, "connection"), linkRows(app, incoming, true), "Nothing the index knows of uses it."),
    ...openSection(open),
  ];
}

const truncationNote = (links, detail) => `${links.length.toLocaleString()}${detail.truncated ? " or more" : ""}`;

function linkRows(app, links, isIncoming) {
  return links.slice(0, MOST_ROWS * 3).map((link) => {
    const name = isIncoming ? link.fromLabel : link.toLabel;
    const place = link.line ? `line ${link.line}` : "";
    const certainty = link.certainty === "exact" ? "" : `, ${link.certainty}`;
    const own = isIncoming ? link.toLabel : link.fromLabel;
    const why = `${relationPhrase(link.kind, isIncoming)} from ${own}${certainty}, ${link.evidence}`;
    const target = app.model.nodes[isIncoming ? link.fromNode : link.toNode];
    return row(isIncoming ? "in" : "out", name, place, null, why, target ? () => app.select(target, { fly: true }) : null);
  });
}

function openSection(open) {
  if (open.length === 0) return [];
  const items = open.slice(0, MOST_ROWS).map((link) => el(
    "div",
    { class: "open-link" },
    el("code", { text: link.hint }),
    el("span", { text: `${link.state === "unresolved" ? "Looked for, not found" : "Not analysed"}: ${link.reason}${link.line ? `, line ${link.line}` : ""}` }),
  ));
  return [el("h2", {}, el("span", { text: "What the index could not resolve" }), el("small", { text: open.length.toLocaleString() })), ...items];
}
