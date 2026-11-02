import { linkSections, listSection, rollUpSections, row } from "./panel-links.js";
import { el, nodeFacts, nodeKindName, plural, relationPhrase } from "./words.js";

const MOST_CHILDREN = 60;

export class Panel {
  constructor(app, host) {
    this.app = app;
    this.host = host;
  }

  showNode(node) {
    const { app } = this;
    const isCode = node.type === "file" || node.type === "symbol";
    const parts = [
      el("h1", { class: isCode ? "code" : "", text: node.label }),
      el("p", { class: "kind", text: nodeKindName(node) }),
    ];
    if (node.path) parts.push(el("p", { class: "path", text: node.path }));
    if (node.type === "symbol" && node.symbol.signature) parts.push(el("p", { class: "signature", text: node.symbol.signature }));
    const facts = nodeFacts(node);
    if (facts) parts.push(el("p", { class: "facts", text: facts }));
    parts.push(this.#actions(node));
    parts.push(...this.#openCount(node));
    if (node.detail) parts.push(...linkSections(app, node, node.detail), ...this.#symbolList(node));
    else if (node.type === "symbol") parts.push(...linkSections(app, node, node.parent.detail ?? fileDetail(node)));
    else parts.push(...rollUpSections(app, node), ...this.#childList(node));
    this.host.replaceChildren(...parts);
    this.host.scrollTop = 0;
  }

  #actions(node) {
    const { app } = this;
    const buttons = [];
    if (answersImpact(node)) {
      buttons.push(el("button", { class: "plain", type: "button", text: "Show what it affects", onclick: () => app.askImpact(node) }));
    }
    if (node !== app.model.root && node.type !== "libraries") {
      buttons.push(el("button", { class: "plain", type: "button", text: app.traceFrom === node ? "Now click the end" : "Trace a path from here", onclick: () => app.beginTrace(node) }));
    }
    if (node.path) {
      buttons.push(el("button", { class: "plain", type: "button", text: "Copy path", onclick: () => navigator.clipboard?.writeText(node.path) }));
    }
    return el("div", { class: "actions" }, ...buttons);
  }

  #openCount(node) {
    const open = node.unresolved + node.notAnalysed;
    if (open === 0 || node.type === "file" || node.type === "symbol") return [];
    return [el("p", { class: "facts", text: `The index could not resolve ${plural(open, "connection")} that start here.` })];
  }

  #symbolList(node) {
    const top = node.children.filter((child) => child.type === "symbol");
    if (top.length === 0) return [];
    const rows = top.slice(0, MOST_CHILDREN).map((child) => row("", child.label, child.symbol.line ? `line ${child.symbol.line}` : "", null, child.symbol.kind, () => this.app.select(child, { fly: true })));
    return listSection("Declares", plural(node.symbols, "symbol"), rows, "");
  }

  #childList(node) {
    if (node.children.length === 0) return [];
    const biggest = [...node.children].sort((a, b) => b.r - a.r);
    const peak = biggest[0].lines || 1;
    const rows = biggest.slice(0, MOST_CHILDREN).map((child) => row("", child.label, nodeSize(child), child.lines / peak, null, () => this.app.select(child, { fly: true })));
    return listSection("Holds", plural(node.children.length, "part"), rows, "");
  }

  showMessage(title, text, isError) {
    this.host.replaceChildren(
      el("h1", { text: title }),
      el("p", { class: isError ? "error" : "empty", text }),
      this.#backButton(),
    );
  }

  showImpact(root, impact) {
    const { app } = this;
    const lens = app.lens.current;
    const parts = [
      el("h1", { class: root.path ? "code" : "", text: root.label }),
      el("p", { class: "kind", text: "What a change here affects" }),
      this.#backButton(),
    ];
    if (impact.error) parts.push(el("p", { class: "error", text: impact.error }));
    const byDistance = groupBy(impact.affected, (entry) => entry.distance);
    for (const [distance, entries] of [...byDistance.entries()].sort((a, b) => a[0] - b[0])) {
      const rows = entries.slice(0, MOST_CHILDREN).map((entry) => this.#impactRow(entry));
      parts.push(...listSection(distance === 1 ? "Directly" : `${distance} steps away`, entries.length.toLocaleString(), rows, ""));
    }
    if (lens.tests.size > 0) {
      const rows = [...lens.tests].slice(0, MOST_CHILDREN).map((node) => row("in", node.label, "", null, node.path, () => app.select(node, { fly: true, keepLens: true })));
      parts.push(...listSection("Tests to run", lens.tests.size.toLocaleString(), rows, ""));
    }
    if (impact.affected.length === 0 && !impact.error) parts.push(el("p", { class: "empty", text: "The index knows of nothing this affects." }));
    if (impact.unresolved > 0) parts.push(el("p", { class: "facts", text: `The answer may be incomplete: ${plural(impact.unresolved, "connection")} on the way could not be resolved.` }));
    this.host.replaceChildren(...parts);
    this.host.scrollTop = 0;
  }

  #impactRow(entry) {
    const node = this.app.model.nodes[entry.node];
    const certainty = entry.certainty === "exact" ? "" : entry.certainty;
    return row("out", entry.symbol ?? node?.label ?? "", certainty, null, node?.path ?? node?.label, () => node && this.app.select(node, { fly: true, keepLens: true }));
  }

  showTask(task) {
    const { app } = this;
    const parts = [
      el("h1", { text: task.query }),
      el("p", { class: "kind", text: confidenceWords(task.confidence) }),
      this.#backButton(),
    ];
    const peak = task.files[0]?.score || 1;
    const fileRows = task.files.slice(0, MOST_CHILDREN).flatMap((file) => {
      const node = app.model.nodes[file.node];
      if (!node) return [];
      return [row("out", node.label, "", file.score / peak, reasonWords(file), () => app.select(node, { fly: true, keepLens: true }))];
    });
    parts.push(...listSection("Start with these files", task.files.length.toLocaleString(), fileRows, "Nothing in the index matches. Try other words."));
    const symbolRows = task.symbols.slice(0, 20).flatMap((symbol) => {
      const node = app.model.nodes[symbol.node];
      return node ? [row("", symbol.name, "", null, node.path ?? node.label, () => app.select(node, { fly: true, keepLens: true }))] : [];
    });
    if (symbolRows.length > 0) parts.push(...listSection("Symbols", task.symbols.length.toLocaleString(), symbolRows, ""));
    const testRows = task.tests.slice(0, 20).flatMap((test) => {
      const node = app.model.nodes[test.node];
      return node ? [row("in", node.label, "", null, node.path, () => app.select(node, { fly: true, keepLens: true }))] : [];
    });
    if (testRows.length > 0) parts.push(...listSection("Tests", task.tests.length.toLocaleString(), testRows, ""));
    this.host.replaceChildren(...parts);
    this.host.scrollTop = 0;
  }

  showTrace(start, goal, path) {
    const parts = [
      el("h1", { text: `From ${start.label} to ${goal.label}` }),
      el("p", { class: "kind", text: path.length ? plural(path.length - 1, "step") : "No path" }),
      this.#backButton(),
    ];
    if (path.length === 0) {
      parts.push(el("p", { class: "empty", text: "No chain of the connections shown leads from one to the other. Turn on more connection kinds and try again." }));
    }
    const rows = path.map((step) => {
      const why = step.kind ? relationPhrase(step.kind, step.reversed) : "start";
      return row("out", step.node.label, "", null, why, () => this.app.select(step.node, { fly: true, keepLens: true }));
    });
    if (rows.length) parts.push(el("ul", { class: "rows" }, ...rows.map((item) => el("li", {}, item))));
    this.host.replaceChildren(...parts);
    this.host.scrollTop = 0;
  }

  #backButton() {
    return el("div", { class: "actions" }, el("button", { class: "plain", type: "button", text: "Clear this view", onclick: () => this.app.clearLens() }));
  }
}

function answersImpact(node) {
  if (node.id.endsWith("//(root files)")) return false;
  return node.type === "file" || node.type === "module" || node.type === "symbol";
}

function fileDetail(node) {
  for (let up = node.parent; up; up = up.parent) if (up.detail) return up.detail;
  return { outgoing: [], incoming: [], open: [], truncated: false };
}

function nodeSize(node) {
  if (node.files > 1) return plural(node.files, "file");
  return node.lines ? plural(node.lines, "line") : "";
}

function groupBy(items, keyOf) {
  const groups = new Map();
  for (const item of items) {
    const key = keyOf(item);
    if (groups.has(key)) groups.get(key).push(item);
    else groups.set(key, [item]);
  }
  return groups;
}

function confidenceWords(confidence) {
  return confidence ? `A ranked starting point, confidence ${String(confidence).toLowerCase()}.` : "A ranked starting point.";
}

const ROLE_WORDS = { edit: "Likely to change", read: "Worth reading", test: "Tests it" };

const SIGNAL_WORDS = {
  "lexical-symbol": "a symbol name matches",
  "lexical-artifact": "the file text matches",
  graph: "connected to a match",
  history: "changed with a match",
};

function reasonWords(file) {
  const reasons = (file.reasons ?? []).map((reason) => SIGNAL_WORDS[reason] ?? reason).join(", ");
  return `${ROLE_WORDS[file.role] ?? file.role}: ${reasons}`;
}
