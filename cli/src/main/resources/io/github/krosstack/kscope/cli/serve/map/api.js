async function getJson(address) {
  const response = await fetch(address, { cache: "no-store" });
  const body = await response.json().catch(() => ({ error: "The server gave an answer that could not be read." }));
  if (!response.ok) throw new Error(body.error ?? `The server answered ${response.status}.`);
  body.mapVersion = response.headers.get("X-Map-Version");
  return body;
}

// The server leaves out empty lists, so each answer gets them back here and the rest of the page can rely on them.
const withLists = (names) => (body) => ({ ...Object.fromEntries(names.map((name) => [name, []])), ...body });

export const fetchMap = () => getJson("api/map");

export const fetchNode = (id) =>
  getJson(`api/node?id=${encodeURIComponent(id)}`).then(withLists(["symbols", "outgoing", "incoming", "open"]));

export const fetchImpact = (node) => {
  const query = node.sid ? `symbol=${encodeURIComponent(node.sid)}` : `id=${encodeURIComponent(node.id)}`;
  return getJson(`api/impact?${query}`).then(withLists(["affected", "tests"]));
};

export const fetchTask = (text) =>
  getJson(`api/task?text=${encodeURIComponent(text)}`).then(withLists(["files", "tests", "symbols", "modules", "terms"]));
