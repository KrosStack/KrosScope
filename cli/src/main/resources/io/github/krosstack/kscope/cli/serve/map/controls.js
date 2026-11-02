import { COLOUR_MODES } from "./palette.js";
import { chipName, el } from "./words.js";

const OFF_AT_START = new Set(["imports", "co-changes-with"]);
const CERTAINTY_WORDS = { exact: "exact", probable: "probable", heuristic: "best guess" };

export class Filters {
  constructor(app) {
    this.app = app;
    this.offKinds = new Set();
    this.offCertainties = new Set();
    this.colourMode = "place";
    this.showLinks = true;
    this.known = null;
  }

  kindOn(index) {
    return !this.offKinds.has(index);
  }

  certaintyOn(index) {
    return !this.offCertainties.has(index);
  }

  render(host, model) {
    const fingerprint = model.kinds.join();
    if (this.known !== fingerprint) {
      this.offKinds = new Set(model.kinds.flatMap((kind, i) => (OFF_AT_START.has(kind) ? [i] : [])));
      this.known = fingerprint;
    }
    const pulse = host.querySelector(".pulse");
    host.replaceChildren(this.#kindChips(model), this.#certaintyChips(model), this.#colourPicker());
    if (pulse) host.append(pulse);
  }

  #kindChips(model) {
    const used = new Set();
    model.forEachEdge((_from, _to, kind) => used.add(kind));
    const chips = model.kinds.flatMap((kind, i) => (used.has(i) ? [this.#kindChip(kind, i)] : []));
    return el("div", { class: "chips", role: "group", "aria-label": "Connections shown" }, ...chips);
  }

  #kindChip(kind, index) {
    return toggleChip(chipName(kind), !this.offKinds.has(index), (on) => {
      if (on) this.offKinds.delete(index);
      else this.offKinds.add(index);
      this.app.links.invalidate();
    });
  }

  #certaintyChips(model) {
    const chips = model.certainties.map((name, i) => {
      const chip = toggleChip(CERTAINTY_WORDS[name] ?? name, !this.offCertainties.has(i), (on) => {
        if (on) this.offCertainties.delete(i);
        else this.offCertainties.add(i);
        this.app.links.invalidate();
      });
      chip.prepend(lineSample(i));
      chip.title = `Connections the index established as ${name}`;
      return chip;
    });
    return el("div", { class: "chips", role: "group", "aria-label": "Certainty shown" }, ...chips);
  }

  #colourPicker() {
    const select = el("select", { "aria-label": "Colour the bubbles by" });
    for (const mode of COLOUR_MODES) {
      const option = el("option", { value: mode.id, text: `Colour by ${mode.label}` });
      option.selected = mode.id === this.colourMode;
      select.append(option);
    }
    select.addEventListener("change", () => {
      this.colourMode = select.value;
      this.app.repaint();
    });
    return select;
  }
}

function toggleChip(text, on, change) {
  const chip = el("button", { class: "chip", type: "button", text, "aria-pressed": String(on) });
  chip.addEventListener("click", () => {
    const next = chip.getAttribute("aria-pressed") !== "true";
    chip.setAttribute("aria-pressed", String(next));
    change(next);
  });
  return chip;
}

function lineSample(certainty) {
  const dashes = ["7 3", "3 3", "1 3"][certainty] ?? "1 3";
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("width", "22");
  svg.setAttribute("height", "4");
  svg.setAttribute("aria-hidden", "true");
  const line = document.createElementNS(ns, "line");
  line.setAttribute("x1", "0");
  line.setAttribute("x2", "22");
  line.setAttribute("y1", "2");
  line.setAttribute("y2", "2");
  line.setAttribute("stroke", "currentColor");
  line.setAttribute("stroke-width", "2");
  line.setAttribute("stroke-dasharray", dashes);
  svg.append(line);
  return svg;
}
