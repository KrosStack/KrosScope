const LEFT_OUT = [
  ["notExpanded", "symbols whose neighbours were not read"],
  ["membersNotRead", "members that were not read"],
  ["beyondDepth", "relations beyond the depth"],
  ["notFollowed", "relations of symbols that were followed one way only"],
  ["notCallable", "relations to something that is not a function"],
  ["notInType", "relations to code that is in no type"],
  ["withoutSymbol", "relations to something the index holds no symbol for"],
  ["withinType", "relations inside one type"],
  ["stopped", "relations that the limits stopped"],
];

const INHERITANCE = new Set(["extends", "implements"]);

const readable = (name) => name.toLowerCase().replaceAll("_", " ");

const total = (counts) => Object.values(counts ?? {}).reduce((sum, count) => sum + count, 0);

const listed = (counts) => Object.entries(counts ?? {}).map(([name, count]) => `${readable(name)} ${count}`).join(", ");

export const relationOf = (edge) => edge.label.split(";")[0].trim();

export const isInheritance = (edge) => INHERITANCE.has(relationOf(edge));

export function stateClass(weakest) {
  if (weakest === "EXACT") return "exact";
  return weakest === "PROBABLE" || weakest === "HEURISTIC" ? "probable" : "open";
}

// The text on a line: the total for lines that stand for several kinds, the inheritance word, else the call position.
export function edgeText(edge) {
  if (edge.byKind && Object.keys(edge.byKind).length > 0) return String(total(edge.byKind));
  const relation = relationOf(edge);
  if (INHERITANCE.has(relation)) return relation;
  return edge.position == null ? "" : String(edge.position);
}

export function edgeHover(edge) {
  const lines = [relationOf(edge)];
  if (edge.byKind && Object.keys(edge.byKind).length > 0) lines.push(listed(edge.byKind));
  if (edge.position != null) lines.push(`call ${edge.position} of the caller`);
  lines.push(`state: ${listed(edge.byState)}`);
  lines.push(`evidence: ${listed(edge.byEvidence)}`);
  return lines.join("\n");
}

export function omissionLine(omitted) {
  const cut = total(omitted.linksByState);
  const parts = cut > 0 ? [`${cut.toLocaleString()} lines over the limit`] : [];
  for (const [key, words] of LEFT_OUT) {
    if ((omitted[key] ?? 0) > 0) parts.push(`${omitted[key].toLocaleString()} ${words}`);
  }
  return parts.length > 0 ? `Left out: ${parts.join("; ")}.` : "Nothing was left out.";
}
