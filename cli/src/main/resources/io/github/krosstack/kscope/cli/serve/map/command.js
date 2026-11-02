import { el } from "./words.js";

const MOST_RESULTS = 8;
const SEARCHED_TYPES = new Set(["file", "module", "repository", "source-set", "directory", "library"]);

// Characters in order score more when they are adjacent and when they fall in the file name, not its directory.
function matchScore(query, label, path) {
  const inLabel = subsequenceScore(query, label.toLowerCase());
  if (inLabel > 0) return inLabel + 40 + (label.toLowerCase().startsWith(query) ? 40 : 0) - label.length / 40;
  const inPath = path ? subsequenceScore(query, path.toLowerCase()) : 0;
  return inPath > 0 ? inPath - path.length / 40 : 0;
}

function subsequenceScore(query, text) {
  let score = 0;
  let from = 0;
  let previous = -2;
  for (const char of query) {
    const at = text.indexOf(char, from);
    if (at < 0) return 0;
    score += at === previous + 1 ? 5 : 1;
    previous = at;
    from = at + 1;
  }
  return score;
}

export class CommandBox {
  constructor(app, input, list) {
    this.app = app;
    this.input = input;
    this.list = list;
    this.results = [];
    this.cursor = 0;
    input.addEventListener("input", () => this.#update());
    input.addEventListener("keydown", (event) => this.#key(event));
    input.addEventListener("blur", () => setTimeout(() => this.#close(), 150));
    input.addEventListener("focus", () => this.#update());
  }

  focus() {
    this.input.focus();
    this.input.select();
  }

  #update() {
    const text = this.input.value.trim();
    if (!text || !this.app.model?.root) {
      this.#close();
      return;
    }
    this.results = this.#matches(text.toLowerCase());
    if (text.includes(" ") || this.results.length === 0) this.results.unshift({ ask: text });
    else this.results.push({ ask: text });
    this.cursor = 0;
    this.#render();
  }

  #matches(query) {
    return this.app.model.nodes
      .filter((node) => SEARCHED_TYPES.has(node.type))
      .map((node) => ({ node, score: matchScore(query, node.label, node.path) }))
      .filter((entry) => entry.score > 0)
      .sort((a, b) => b.score - a.score || a.node.tin - b.node.tin)
      .slice(0, MOST_RESULTS);
  }

  #render() {
    const rows = this.results.map((result, i) => this.#row(result, i));
    this.list.replaceChildren(...rows);
    this.list.hidden = rows.length === 0;
  }

  #row(result, index) {
    const row = el("li", { role: "option", "aria-selected": String(index === this.cursor) });
    row.addEventListener("mousedown", (event) => {
      event.preventDefault();
      this.#choose(result);
    });
    if (result.ask) {
      row.append(el("span", { class: "ask", text: `Show what “${result.ask}” touches` }));
    } else {
      row.append(el("span", { text: result.node.label }));
      row.append(el("span", { class: "where", text: result.node.path ?? result.node.type }));
    }
    return row;
  }

  #key(event) {
    if (event.key === "Escape") {
      this.input.blur();
      return;
    }
    if (this.results.length === 0) return;
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const step = event.key === "ArrowDown" ? 1 : -1;
      this.cursor = (this.cursor + step + this.results.length) % this.results.length;
      this.#render();
    } else if (event.key === "Enter") {
      event.preventDefault();
      this.#choose(this.results[this.cursor]);
    }
  }

  #choose(result) {
    this.#close();
    if (result.ask) {
      this.app.askTask(result.ask);
    } else {
      this.input.value = "";
      this.app.select(result.node, { fly: true });
    }
  }

  #close() {
    this.results = [];
    this.list.hidden = true;
  }
}
