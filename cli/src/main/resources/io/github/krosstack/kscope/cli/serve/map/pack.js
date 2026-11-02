// Front-chain circle packing (Wang et al., 2006), following the d3-hierarchy implementation; see the notice in
// docs/third-party-licenses.md.

const SLACK = 1e-6;

function place(b, a, c) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const d2 = dx * dx + dy * dy;
  if (d2 === 0) {
    c.x = a.x + a.r + c.r;
    c.y = a.y;
    return;
  }
  const a2 = (a.r + c.r) ** 2;
  const b2 = (b.r + c.r) ** 2;
  if (a2 > b2) {
    const x = (d2 + b2 - a2) / (2 * d2);
    const y = Math.sqrt(Math.max(0, b2 / d2 - x * x));
    c.x = b.x - x * dx - y * dy;
    c.y = b.y - x * dy + y * dx;
  } else {
    const x = (d2 + a2 - b2) / (2 * d2);
    const y = Math.sqrt(Math.max(0, a2 / d2 - x * x));
    c.x = a.x + x * dx - y * dy;
    c.y = a.y + x * dy + y * dx;
  }
}

function overlaps(a, b) {
  const gap = a.r + b.r - SLACK;
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  return gap > 0 && gap * gap > dx * dx + dy * dy;
}

function distanceFromOrigin(link) {
  const a = link.circle;
  const b = link.next.circle;
  const total = a.r + b.r;
  const x = (a.x * b.r + b.x * a.r) / total;
  const y = (a.y * b.r + b.y * a.r) / total;
  return x * x + y * y;
}

function firstOverlap(left, right, circle) {
  let forward = right.next;
  let backward = left.previous;
  let reachForward = right.circle.r;
  let reachBackward = left.circle.r;
  do {
    if (reachForward <= reachBackward) {
      if (overlaps(forward.circle, circle)) return { link: forward, side: "right" };
      reachForward += forward.circle.r;
      forward = forward.next;
    } else {
      if (overlaps(backward.circle, circle)) return { link: backward, side: "left" };
      reachBackward += backward.circle.r;
      backward = backward.previous;
    }
  } while (forward !== backward.next);
  return null;
}

function closestToOrigin(start, stop) {
  let best = start;
  let score = distanceFromOrigin(start);
  for (let link = start.next; link !== stop; link = link.next) {
    const candidate = distanceFromOrigin(link);
    if (candidate < score) {
      best = link;
      score = candidate;
    }
  }
  return best;
}

function seed(circles) {
  const [first, second] = circles;
  first.x = -second.r;
  first.y = 0;
  second.x = first.r;
  second.y = 0;
}

export function packSiblings(circles) {
  if (circles.length === 0) return;
  circles[0].x = 0;
  circles[0].y = 0;
  if (circles.length === 1) return;
  seed(circles);
  if (circles.length === 2) return;
  place(circles[1], circles[0], circles[2]);
  const a = { circle: circles[0] };
  const b = { circle: circles[1] };
  const c = { circle: circles[2] };
  a.next = c.previous = b;
  b.next = a.previous = c;
  c.next = b.previous = a;
  let left = a;
  let right = b;
  for (let i = 3; i < circles.length; i++) {
    const circle = circles[i];
    place(left.circle, right.circle, circle);
    const hit = firstOverlap(left, right, circle);
    if (hit) {
      if (hit.side === "right") right = hit.link;
      else left = hit.link;
      left.next = right;
      right.previous = left;
      i--;
      continue;
    }
    const link = { circle, previous: left, next: right };
    left.next = link;
    right.previous = link;
    right = link;
    left = closestToOrigin(left, right);
    right = left.next;
  }
}

// Each step moves towards the circle that sticks out furthest by a shrinking share: the core-set scheme, which gives a
// circle within a percent or two of the smallest for far less code than an exact solver.
export function enclosing(circles) {
  if (circles.length === 1) return { x: circles[0].x, y: circles[0].y, r: circles[0].r };
  let x = 0;
  let y = 0;
  let weight = 0;
  for (const c of circles) {
    x += c.x * c.r;
    y += c.y * c.r;
    weight += c.r;
  }
  x /= weight || 1;
  y /= weight || 1;
  const steps = Math.min(90, 20 + circles.length);
  for (let step = 1; step <= steps; step++) {
    const far = furthest(circles, x, y);
    const share = 1 / (step + 1);
    x += (far.c.x - x) * share;
    y += (far.c.y - y) * share;
  }
  return { x, y, r: furthest(circles, x, y).reach };
}

function furthest(circles, x, y) {
  let c = circles[0];
  let reach = -1;
  for (const circle of circles) {
    const distance = Math.hypot(circle.x - x, circle.y - y) + circle.r;
    if (distance > reach) {
      reach = distance;
      c = circle;
    }
  }
  return { c, reach };
}
