// Bright field: dyes on glass, which darken where they overlap. Fluorescence: light on black, which adds up.
const THEMES = {
  light: {
    blend: "multiply",
    linkGain: 1,
    ink: "#1f1a38",
    inkSoft: "#5a5670",
    halo: "rgba(247,249,246,.86)",
    outgoing: "#c9416f",
    incoming: "#2a7ba6",
    context: "#3f3192",
    added: "#1f8a68",
    changed: "#a9791f",
    removed: "#c9416f",
    dim: "#aeb6b1",
    stains: ["#3f3192", "#c9416f", "#2a7ba6", "#a9791f", "#1f8a68", "#8c3fa8", "#b3462f", "#3b6ea5", "#6d7f24", "#a53a8f"],
    heat: ["#f3c9b4", "#e08a6f", "#b83a4b", "#6d1f4f"],
    cool: "#c7d8df",
  },
  dark: {
    blend: "lighter",
    linkGain: 0.45,
    ink: "#e6ecff",
    inkSoft: "#8d97b8",
    halo: "rgba(4,6,12,.82)",
    outgoing: "#ff6d8e",
    incoming: "#4fdcef",
    context: "#7f9bff",
    added: "#52ec93",
    changed: "#f3dc62",
    removed: "#ff6d8e",
    dim: "#2b3350",
    stains: ["#7f9bff", "#52ec93", "#ff6d8e", "#f3dc62", "#4fdcef", "#d27bff", "#ff9a5c", "#8cf0c9", "#c6e85a", "#ff8fd8"],
    heat: ["#4b3b7a", "#a1408f", "#f0587a", "#ffd166"],
    cool: "#1b2d4d",
  },
};

const CATEGORY_ORDER = ["source", "build", "resource", "schema", "config", "native", "documentation", "other"];

const hashText = (text) => {
  let hash = 2166136261;
  for (let i = 0; i < text.length; i++) hash = Math.imul(hash ^ text.charCodeAt(i), 16777619);
  return hash >>> 0;
};

export const COLOUR_MODES = [
  { id: "place", label: "where it lives" },
  { id: "category", label: "kind of file" },
  { id: "reach", label: "most used" },
  { id: "activity", label: "most changed" },
  { id: "blind", label: "what the index could not read" },
];

export function theme(name) {
  return THEMES[name];
}

function colouringRoot(node, model) {
  const repositories = model.root.children.filter((child) => child.type === "repository").length;
  const anchor = repositories > 1 ? "repository" : "module";
  for (let current = node; current; current = current.parent) {
    if (current.type === anchor || current.type === "libraries") return current;
  }
  return null;
}

function stainOf(node, model, stains) {
  const root = colouringRoot(node, model);
  return stains[root ? hashText(root.id) % stains.length : 0];
}

const isLeaf = (node) => node.type === "file" || node.children.length === 0;

const mix = (scale, share) => scale[Math.min(scale.length - 1, Math.floor(Math.max(0, share) * scale.length))];

function maxima(model) {
  let reach = 1;
  let activity = 1;
  for (const node of model.nodes) {
    if (!isLeaf(node)) continue;
    reach = Math.max(reach, node.incoming);
    activity = Math.max(activity, node.commits);
  }
  return { reach, activity };
}

function blindShare(node) {
  const open = node.unresolved + node.notAnalysed;
  return open === 0 ? 0 : Math.min(1, 0.25 + open / (open + node.outgoing + 4));
}

function leafColour(node, model, mode, palette, peaks) {
  switch (mode) {
    case "category": {
      const position = CATEGORY_ORDER.indexOf(node.category);
      return node.category ? palette.stains[Math.max(position, 0)] : palette.dim;
    }
    case "reach":
      return node.incoming === 0 ? palette.cool : mix(palette.heat, Math.log1p(node.incoming) / Math.log1p(peaks.reach));
    case "activity":
      return node.commits === 0 ? palette.cool : mix(palette.heat, Math.log1p(node.commits) / Math.log1p(peaks.activity));
    case "blind": {
      const share = blindShare(node);
      return share === 0 ? palette.cool : mix(palette.heat, share);
    }
    default:
      return stainOf(node, model, palette.stains);
  }
}

const FAMILY_COLOUR_MODES = new Set(["place"]);

export function paint(model, mode, palette) {
  const peaks = maxima(model);
  for (const node of model.nodes) {
    node.colour = isLeaf(node) || FAMILY_COLOUR_MODES.has(mode)
      ? leafColour(node, model, mode, palette, peaks)
      : palette.inkSoft;
  }
}

export function symbolColour(symbol, palette) {
  return palette.stains[hashText(symbol.category ?? symbol.kind ?? "") % palette.stains.length];
}
