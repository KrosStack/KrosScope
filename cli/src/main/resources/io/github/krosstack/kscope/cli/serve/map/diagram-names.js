const MAX_SHOWN = 36;
const ID_FIELDS = 4;

function quoted(text, from) {
  let name = "";
  for (let at = from + 1; at < text.length; at++) {
    if (text[at] !== "`") name += text[at];
    else if (text[at + 1] === "`") name += text[at++];
    else return { name, end: at + 1 };
  }
  return null;
}

function plain(text, from) {
  let end = from;
  while (end < text.length && /[\w$+<>-]/.test(text[end])) end++;
  return end > from ? { name: text.slice(from, end), end } : null;
}

function closing(text, from) {
  let depth = 0;
  for (let at = from; at < text.length; at++) {
    if (text[at] === "(") depth++;
    else if (text[at] === ")" && --depth === 0) return at;
  }
  return -1;
}

function descriptorsOf(symbolId) {
  let start = 0;
  for (let field = 0; field < ID_FIELDS; field++) {
    start = symbolId.indexOf(" ", start) + 1;
    if (start === 0) return "";
  }
  return symbolId.slice(start);
}

function partsOf(text) {
  const packages = [];
  const names = [];
  let at = 0;
  while (at < text.length) {
    if (text[at] === "[" || text[at] === "(") return null;
    const part = text[at] === "`" ? quoted(text, at) : plain(text, at);
    if (!part) return null;
    at = part.end;
    const mark = text[at];
    if (mark === "/") {
      packages.push(part.name);
      at++;
    } else if (mark === ":") at++;
    else if (mark === "#" || mark === ".") {
      names.push(part.name);
      at++;
    } else if (mark === "(") {
      const close = closing(text, at);
      if (close < 0 || text[close + 1] !== ".") return null;
      names.push(part.name);
      at = close + 2;
    } else return null;
  }
  return { packages, names };
}

const partsOfNode = (node) => {
  const parts = node.symbolId ? partsOf(descriptorsOf(node.symbolId)) : null;
  return parts && parts.names.length > 0 ? parts : null;
};

export function shortName(node) {
  const parts = partsOfNode(node);
  return parts ? parts.names.join(".") : node.label;
}

export function qualifiedName(node) {
  const parts = partsOfNode(node);
  return parts ? [...parts.packages, ...parts.names].join(".") : node.label;
}

export const cutAtStart = (text) => (text.length > MAX_SHOWN ? `…${text.slice(text.length - (MAX_SHOWN - 1))}` : text);

export const shownName = (node) => cutAtStart(shortName(node));
