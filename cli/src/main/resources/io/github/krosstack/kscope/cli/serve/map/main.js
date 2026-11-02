import { fetchImpact, fetchMap, fetchNode, fetchTask } from "./api.js";
import { Camera } from "./camera.js";
import { CommandBox } from "./command.js";
import { Filters } from "./controls.js";
import { Lens, tracePath } from "./lens.js";
import { Links } from "./links.js";
import { diffModels, Live, renderChanges } from "./live.js";
import { MapModel } from "./model.js";
import { paint, theme } from "./palette.js";
import { Panel } from "./panel.js";
import { Scene } from "./scene.js";
import { el, nodeFacts, nodeKindName } from "./words.js";

const OPEN_FILE_RADIUS = 64;
const MORPH_MILLIS = 800;
const WHEEL_SPEED = 0.0016;
const DRAG_THRESHOLD = 4;
const THEME_KEY = "kscope-map-theme";

const byId = (id) => document.getElementById(id);

class MapApp {
  constructor() {
    this.canvas = byId("map");
    this.context = this.canvas.getContext("2d");
    this.model = null;
    this.selected = null;
    this.hover = null;
    this.traceFrom = null;
    this.question = 0;
    this.filters = new Filters(this);
    this.camera = new Camera(() => this.#cameraMoved());
    this.scene = new Scene(this, this.context);
    this.links = new Links(this);
    this.lens = new Lens(this);
    this.panel = new Panel(this, byId("panel"));
    this.command = new CommandBox(this, byId("command"), byId("results"));
    this.live = new Live(this, () => this.#reload());
    this.framePending = false;
    this.morph = null;
    this.ghosts = [];
    this.settleTimer = 0;
    this.#useTheme(this.#startingTheme());
    this.#listen();
    this.#resize();
    byId("controls").append(this.live.indicator);
  }

  requestFrame() {
    if (this.framePending) return;
    this.framePending = true;
    requestAnimationFrame((now) => this.#frame(now));
  }

  openFiles() {
    const open = new Set();
    if (!this.model?.root) return open;
    for (const node of this.model.nodes) if (node.detail && this.scene.isOpen(node)) open.add(node);
    return open;
  }

  repaint() {
    if (!this.model?.root) return;
    paint(this.model, this.filters.colourMode, this.palette);
    this.requestFrame();
  }

  select(node, { fly = false, keepLens = false, remember = true } = {}) {
    if (this.traceFrom && this.traceFrom !== node) {
      this.#finishTrace(node);
      return;
    }
    if (!keepLens) {
      this.question++;
      this.lens.clear();
    }
    this.selected = node;
    this.#drawTrail(node);
    if (fly) this.camera.flyTo(node);
    if (!keepLens) {
      this.panel.showNode(node);
      if (node.type === "file" && !node.detail) this.#loadDetail(node);
    }
    this.links.invalidate();
    if (remember) this.#remember(node);
  }

  selectById(id) {
    const node = this.model?.byId.get(id);
    if (node) this.select(node, { fly: true });
  }

  clearLens() {
    this.traceFrom = null;
    this.lens.clear();
    this.select(this.selected ?? this.model.root, { remember: false });
  }

  beginTrace(node) {
    this.traceFrom = node;
    this.panel.showNode(node);
    this.selected = node;
    this.links.invalidate();
  }

  async askImpact(node) {
    const question = ++this.question;
    this.panel.showMessage(node.label, "Working out what it affects.", false);
    try {
      const impact = await this.#askCurrentMap(() => fetchImpact(node));
      if (question !== this.question) return;
      const root = this.model.byId.get(node.id) ?? node;
      this.lens.showImpact(root, impact);
      this.panel.showImpact(root, impact);
    } catch (failure) {
      if (question === this.question) this.panel.showMessage(node.label, failure.message, true);
    }
  }

  async askTask(text) {
    const question = ++this.question;
    this.panel.showMessage(text, "Looking through the index.", false);
    try {
      const task = await this.#askCurrentMap(() => fetchTask(text));
      if (question !== this.question) return;
      this.lens.showTask(task);
      this.panel.showTask(task);
      const best = this.model.nodes[task.files[0]?.node];
      if (best) this.#frameAround(task.files.slice(0, 8).map((file) => this.model.nodes[file.node]).filter(Boolean));
    } catch (failure) {
      if (question === this.question) this.panel.showMessage(text, failure.message, true);
    }
  }

  // An answer names nodes by their place in one map, so it only means something on that same map.
  async #askCurrentMap(ask) {
    const first = await ask();
    if (first.mapVersion === this.model?.version) return first;
    await this.#reload();
    const second = await ask();
    if (second.mapVersion === this.model?.version) return second;
    throw new Error("The index changed while answering. Ask again.");
  }

  #frameAround(nodes) {
    let holder = nodes[0];
    for (const node of nodes) while (!this.model.contains(holder, node) && holder.parent) holder = holder.parent;
    this.camera.flyTo(holder);
  }

  #finishTrace(goal) {
    const start = this.traceFrom;
    this.traceFrom = null;
    this.question++;
    const path = tracePath(this.model, this.filters, start, goal);
    this.lens.showTrace(path);
    this.panel.showTrace(start, goal, path);
    this.selected = goal;
    this.#drawTrail(goal);
    this.#frameAround([start, goal, ...path.map((step) => step.node)]);
    this.links.invalidate();
  }

  async #loadDetail(file) {
    if (file.detailPending) return;
    file.detailPending = true;
    try {
      const model = this.model;
      const detail = await fetchNode(file.id);
      if (this.model !== model || detail.mapVersion !== model.version) return;
      file.detail = detail;
      this.#whenSettled(() => {
        if (detail.symbols.length > 0) this.model.addSymbols(file, detail.symbols);
        if (this.selected === file && !this.lens.active) this.panel.showNode(file);
        this.links.invalidate();
      });
    } catch {
      file.detail = null;
    } finally {
      file.detailPending = false;
    }
  }

  // Symbols take their place from the file's final position and size, which a running morph has not reached yet.
  #whenSettled(work) {
    if (this.morph) this.morph.afterwards.push(work);
    else work();
  }

  #cameraMoved() {
    this.requestFrame();
    clearTimeout(this.settleTimer);
    this.settleTimer = setTimeout(() => this.#settled(), 110);
  }

  #settled() {
    this.links.invalidate();
    this.#openVisibleFiles();
  }

  #openVisibleFiles() {
    if (!this.model?.root) return;
    const { camera } = this;
    const wanted = (node) => node.type === "file" && !node.detail && node.r * camera.k > OPEN_FILE_RADIUS && camera.visible(node);
    const visit = (node) => {
      if (wanted(node)) this.#loadDetail(node);
      if (node.r * camera.k > 30 && camera.visible(node)) node.children.forEach(visit);
    };
    visit(this.model.root);
  }

  async #reload() {
    let overview;
    try {
      overview = await fetchMap();
    } catch (failure) {
      this.#notice(failure.message);
      return;
    }
    const model = new MapModel(overview);
    if (!model.root || model.nodes.length <= 1) {
      this.model = null;
      this.#notice("no-index");
      return;
    }
    this.#notice(null);
    this.#adopt(model);
  }

  #adopt(model) {
    const before = this.model;
    const changes = diffModels(before, model);
    this.#startMorph(before, model, changes);
    this.model = model;
    this.filters.render(byId("controls"), model);
    this.repaint();
    if (before) renderChanges(byId("changes"), changes, this);
    const selected = this.#restoreSelection(before);
    if (!before) this.#openingFlight(selected);
    this.#keepOpenFiles(before);
    this.#openVisibleFiles();
    this.links.invalidate();
  }

  #openingFlight(target) {
    Object.assign(this.camera, this.camera.framing(this.model.root));
    this.camera.k *= 0.78;
    this.camera.flyTo(target);
  }

  // Answers on screen point at the nodes of the old map and may no longer hold, so a new index ends the lens.
  #restoreSelection(before) {
    const wanted = decodeURIComponent(location.hash.slice(1));
    const previous = before && this.selected ? this.selected.id : wanted;
    const node = this.model.byId.get(previous) ?? this.#nearestSurvivor(before) ?? this.model.root;
    this.traceFrom = null;
    const pending = this.question;
    this.select(node, { remember: false });
    this.question = pending;
    return node;
  }

  #nearestSurvivor(before) {
    if (!before || !this.selected) return null;
    for (let up = this.selected.parent; up; up = up.parent) {
      const found = this.model.byId.get(up.id);
      if (found) return found;
    }
    return null;
  }

  #keepOpenFiles(before) {
    if (!before) return;
    for (const node of before.nodes) {
      const fresh = this.model.byId.get(node.id);
      if (node.detail && fresh && fresh.type === "file") this.#loadDetail(fresh);
    }
  }

  #startMorph(before, model, changes) {
    if (!before || !this.camera.motionAllowed) return;
    const from = new Map();
    for (const node of model.nodes) {
      const known = before.byId.get(node.id);
      const seed = known ?? (node.parent ? before.byId.get(node.parent.id) : null);
      from.set(node, seed ? { x: seed.x, y: seed.y, r: known ? seed.r : 0 } : { x: node.x, y: node.y, r: node.r });
    }
    const to = new Map(model.nodes.map((node) => [node, { x: node.x, y: node.y, r: node.r }]));
    this.morph = { start: performance.now(), from, to, model, afterwards: [] };
    const now = performance.now();
    for (const { node, change } of changes) {
      if (change === "removed") continue;
      // A changed file is often too small to see at this zoom, so the bubbles that hold it pulse with it.
      for (let up = model.byId.get(node.id); up?.parent; up = up.parent) {
        up.pulseAt = now + MORPH_MILLIS * 0.5;
        up.pulseKind = change;
      }
    }
  }

  #stepMorph(now) {
    const morph = this.morph;
    if (!morph) return;
    const t = Math.min(1, (now - morph.start) / MORPH_MILLIS);
    const eased = 1 - (1 - t) ** 3;
    for (const [node, from] of morph.from) {
      const to = morph.to.get(node);
      node.x = from.x + (to.x - from.x) * eased;
      node.y = from.y + (to.y - from.y) * eased;
      node.r = from.r + (to.r - from.r) * eased;
    }
    if (t === 1) {
      this.morph = null;
      morph.afterwards.forEach((work) => work());
      this.links.invalidate();
    } else {
      this.requestFrame();
    }
  }

  #frame(now) {
    this.framePending = false;
    const flying = this.camera.step(now);
    this.#stepMorph(now);
    const { context: ctx, canvas } = this;
    const scale = window.devicePixelRatio || 1;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (this.model?.root) {
      this.scene.draw(this.palette, now);
      this.links.draw(ctx, this.camera, this.palette, now);
      this.scene.drawLabels(this.palette);
    }
    if (flying || this.morph || this.#flowing()) this.requestFrame();
    if (flying && !this.camera.flight) this.#settled();
  }

  #flowing() {
    return this.camera.motionAllowed && this.selected && this.selected !== this.model?.root && this.filters.showLinks;
  }

  #notice(message) {
    const host = byId("notice");
    if (!message) {
      host.hidden = true;
      return;
    }
    if (message === "no-index") {
      host.replaceChildren(el("p", {}, "This workspace has no index yet. Run ", el("code", { text: "kscope scan" }), " in it, and the map fills in here by itself."));
    } else {
      host.replaceChildren(el("p", { text: message }));
    }
    host.hidden = false;
  }

  #drawTrail(node) {
    const steps = this.model.ancestors(node).map((step, i) => el("button", {
      type: "button",
      class: i ? "step" : "",
      text: step.label,
      onclick: () => this.select(step, { fly: true }),
    }));
    byId("trail").replaceChildren(...steps);
  }

  #remember(node) {
    const hash = `#${encodeURIComponent(node.id)}`;
    if (location.hash !== hash) history.pushState(null, "", hash);
  }

  #useTheme(name) {
    this.themeName = name;
    this.palette = theme(name);
    document.documentElement.dataset.theme = name;
    byId("theme").textContent = name === "light" ? "Fluorescence" : "Bright field";
    this.repaint();
  }

  #startingTheme() {
    try {
      const saved = localStorage.getItem(THEME_KEY);
      if (saved === "light" || saved === "dark") return saved;
    } catch {
      return "light";
    }
    return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  #toggleTheme() {
    const next = this.themeName === "light" ? "dark" : "light";
    this.#useTheme(next);
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      return;
    }
  }

  #resize() {
    const box = byId("field").getBoundingClientRect();
    const scale = window.devicePixelRatio || 1;
    this.canvas.width = Math.round(box.width * scale);
    this.canvas.height = Math.round(box.height * scale);
    this.camera.resize(box.width, box.height);
    this.links.invalidate();
  }

  #listen() {
    window.addEventListener("resize", () => this.#resize());
    window.addEventListener("popstate", () => {
      const node = this.model?.byId.get(decodeURIComponent(location.hash.slice(1))) ?? this.model?.root;
      if (node) this.select(node, { fly: true, remember: false });
    });
    window.addEventListener("keydown", (event) => this.#globalKey(event));
    byId("theme").addEventListener("click", () => this.#toggleTheme());
    byId("home").addEventListener("click", (event) => {
      event.preventDefault();
      if (this.model?.root) this.select(this.model.root, { fly: true });
    });
    this.#listenPointer();
  }

  #globalKey(event) {
    const typing = event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement;
    if (typing || event.ctrlKey || event.metaKey || event.altKey) return;
    if (event.key === "/") {
      event.preventDefault();
      this.command.focus();
    } else if (event.key === "Escape" && this.model?.root) {
      this.#stepBack();
    } else if (event.key === "l") {
      this.filters.showLinks = !this.filters.showLinks;
      this.links.invalidate();
    }
  }

  #stepBack() {
    if (this.traceFrom) {
      this.traceFrom = null;
      this.panel.showNode(this.selected);
    } else if (this.lens.active) {
      this.clearLens();
    } else if (this.selected?.parent) {
      this.select(this.selected.parent, { fly: true });
    }
  }

  #listenPointer() {
    const { canvas } = this;
    let drag = null;
    canvas.addEventListener("wheel", (event) => {
      event.preventDefault();
      if (!this.model?.root) return;
      this.camera.zoomAround(event.offsetX, event.offsetY, Math.exp(-event.deltaY * WHEEL_SPEED), this.model.root.r);
    }, { passive: false });
    canvas.addEventListener("pointerdown", (event) => {
      canvas.setPointerCapture(event.pointerId);
      drag = { x: event.offsetX, y: event.offsetY, moved: false };
    });
    canvas.addEventListener("pointermove", (event) => {
      if (!drag) {
        this.#hoverAt(event.offsetX, event.offsetY);
        return;
      }
      const dx = event.offsetX - drag.x;
      const dy = event.offsetY - drag.y;
      if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
      drag.moved = true;
      drag.x = event.offsetX;
      drag.y = event.offsetY;
      this.camera.panBy(dx, dy);
    });
    canvas.addEventListener("pointerup", (event) => {
      const clicked = drag && !drag.moved;
      drag = null;
      if (clicked) this.#clickAt(event.offsetX, event.offsetY);
    });
    canvas.addEventListener("pointerleave", () => { byId("tip").hidden = true; });
  }

  #clickAt(sx, sy) {
    if (!this.model?.root) return;
    const node = this.scene.nodeAt(sx, sy) ?? this.model.root;
    this.select(node, { fly: true });
  }

  #hoverAt(sx, sy) {
    const tip = byId("tip");
    if (!this.model?.root) return;
    const node = this.scene.nodeAt(sx, sy);
    if (!node || node === this.model.root) {
      tip.hidden = true;
      return;
    }
    const facts = nodeFacts(node);
    tip.replaceChildren(el("b", { text: node.path ?? node.label }), el("span", { text: [nodeKindName(node), facts].filter(Boolean).join(": ") }));
    tip.hidden = false;
    const room = byId("field").clientWidth;
    tip.style.left = `${Math.min(sx + 14, room - tip.offsetWidth - 8)}px`;
    tip.style.top = `${Math.max(sy - tip.offsetHeight - 10, 6)}px`;
  }
}

new MapApp();
