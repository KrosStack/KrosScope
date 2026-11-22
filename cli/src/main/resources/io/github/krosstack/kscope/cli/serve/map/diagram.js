import { fetchDiagram } from "./api.js";
import { drawDiagram } from "./diagram-draw.js";
import { omissionLine } from "./diagram-words.js";
import { el } from "./words.js";

const DEPTHS = ["1", "2", "3", "all"];

const LEGEND = [
  ["exact", "exact"],
  ["probable", "probable or best guess"],
  ["open", "not resolved or not analysed"],
  ["inherit", "extends or implements"],
];

export function drawsDiagram(node) {
  return node.type === "symbol" && (node.symbol.category === "callable" || node.symbol.category === "type");
}

// The diagram section of the panel for a function or a type. The depth that the user picked stays for the next one.
export class DiagramView {
  constructor(app) {
    this.app = app;
    this.depth = "1";
    this.question = 0;
    this.focusNext = false;
  }

  section(node) {
    const body = el("div", { class: "diagram-body" });
    const buttons = DEPTHS.map((depth) => el("button", {
      type: "button",
      text: depth,
      "aria-pressed": String(depth === this.depth),
      onclick: () => this.#pick(depth, node, body, buttons),
    }));
    const isWide = document.querySelector(".stage")?.classList.contains("wide");
    const widen = el("button", { class: "plain widen", type: "button", text: isWide ? "Narrow the panel" : "Widen the panel", onclick: (event) => this.#widen(event.currentTarget) });
    const tools = el(
      "div",
      { class: "diagram-tools" },
      el("span", { text: "Depth" }),
      el("div", { class: "depth", role: "group", "aria-label": "How many steps from the symbol" }, ...buttons),
      widen,
    );
    const focus = this.focusNext;
    this.focusNext = false;
    this.#load(node, body, focus);
    return [el("h2", {}, el("span", { text: "Diagram" })), tools, body];
  }

  #pick(depth, node, body, buttons) {
    this.depth = depth;
    buttons.forEach((button) => button.setAttribute("aria-pressed", String(button.textContent === depth)));
    this.#load(node, body);
  }

  #widen(button) {
    const stage = button.closest(".stage");
    const wide = stage.classList.toggle("wide");
    button.textContent = wide ? "Narrow the panel" : "Widen the panel";
    window.dispatchEvent(new Event("resize"));
  }

  async #choose(tie, byKeyboard) {
    this.focusNext = byKeyboard;
    try {
      await this.app.selectSymbol(tie);
    } finally {
      this.focusNext = false;
    }
  }

  async #load(node, body, focus = false) {
    const question = ++this.question;
    body.replaceChildren(el("p", { class: "empty", text: "Drawing the diagram." }));
    try {
      const answer = await fetchDiagram(node.sid, this.depth);
      if (question === this.question && body.isConnected) this.#show(body, answer, focus);
    } catch (failure) {
      if (question === this.question && body.isConnected) body.replaceChildren(el("p", { class: "error", text: failure.message }));
    }
  }

  #show(body, answer, focus) {
    if (answer.error || !answer.diagram) {
      body.replaceChildren(el("p", { class: "error", text: answer.error ?? "The server drew no diagram." }));
      return;
    }
    const picture = drawDiagram(answer, (tie, byKeyboard) => this.#choose(tie, byKeyboard));
    const scroll = el("div", { class: "diagram-scroll", tabindex: "0", role: "region", "aria-label": "Diagram, scrolls" }, picture);
    body.replaceChildren(
      scroll,
      el("p", { class: "facts", text: answer.diagram.title }),
      el("p", { class: "facts", text: omissionLine(answer.diagram.omitted) }),
      legend(),
    );
    if (focus) picture.querySelector('[aria-current="true"]')?.focus();
  }
}

function legend() {
  const items = LEGEND.map(([kind, words]) => el("li", {}, el("span", { class: `swatch ${kind}` }), words));
  return el("ul", { class: "diagram-legend", "aria-label": "How lines are drawn" }, ...items);
}
