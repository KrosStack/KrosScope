import { el, plural } from "./words.js";

const MOST_LISTED = 40;
const RECONNECT_MILLIS = 2000;

export class Live {
  constructor(app, onVersion) {
    this.app = app;
    this.onVersion = onVersion;
    this.version = -1;
    this.state = null;
    this.gone = false;
    this.indicator = el("div", { class: "pulse", "data-state": "live", role: "status" }, el("i"), el("span", { text: "Connecting" }));
    this.#connect();
  }

  #connect() {
    const source = new EventSource("api/events");
    source.addEventListener("state", (event) => this.#heard(JSON.parse(event.data)));
    source.addEventListener("error", () => {
      if (source.readyState === EventSource.CLOSED) setTimeout(() => this.#connect(), RECONNECT_MILLIS);
      this.gone = true;
      this.#show();
    });
    source.addEventListener("open", () => {
      this.gone = false;
      this.#show();
    });
  }

  #heard(state) {
    this.gone = false;
    this.state = state;
    this.#show();
    if (state.version !== this.version) {
      this.version = state.version;
      this.onVersion(state);
    }
  }

  #show() {
    const words = this.indicator.lastChild;
    const { state, gone } = this;
    this.indicator.dataset.state = gone ? "gone" : state?.scanning ? "scanning" : "live";
    this.indicator.title = state?.watch ?? "";
    if (gone) words.textContent = "The server stopped. Start kscope serve --map again and this page reconnects.";
    else if (state?.scanning) words.textContent = "Scanning";
    else if (state?.watching) words.textContent = behind(state.watch) ? "Changes are settling" : "Watching for changes";
    else words.textContent = "Live: shows each scan";
  }
}

const behind = (text) => (text ?? "").includes("behind");

export function diffModels(before, after) {
  const changes = [];
  if (!before?.root) return changes;
  for (const node of after.nodes) {
    if (!isTracked(node)) continue;
    const known = before.byId.get(node.id);
    if (!known) changes.push({ node, change: "added" });
    else if (node.hash !== known.hash) changes.push({ node, change: "changed" });
  }
  for (const node of before.nodes) {
    if (isTracked(node) && !after.byId.has(node.id)) changes.push({ node, change: "removed" });
  }
  return changes;
}

const isTracked = (node) => node.type === "file" || node.type === "module" || node.type === "repository";

export function renderChanges(host, changes, app) {
  if (changes.length === 0) return;
  const close = el("button", { type: "button", text: "Dismiss", onclick: () => { host.hidden = true; } });
  const counts = summary(changes);
  const rows = changes.slice(0, MOST_LISTED).map((entry) => changeRow(entry, app));
  host.replaceChildren(
    el("h2", {}, el("span", { text: `Last scan: ${counts}` }), close),
    el("ul", {}, ...rows),
  );
  host.hidden = false;
}

function summary(changes) {
  return ["added", "changed", "removed"]
    .map((word) => [word, changes.filter((entry) => entry.change === word).length])
    .filter(([, count]) => count > 0)
    .map(([word, count]) => `${count} ${word}`)
    .join(", ") || plural(changes.length, "change");
}

function changeRow({ node, change }, app) {
  const mark = { added: "+", changed: "~", removed: "−" }[change];
  const button = el("button", { type: "button", class: change, text: `${mark} ${node.label}`, title: node.path ?? node.label });
  if (change !== "removed") button.addEventListener("click", () => app.selectById(node.id));
  return el("li", {}, button);
}
