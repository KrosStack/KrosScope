const FLIGHT_MILLIS = 620;
const MIN_ZOOM_SHARE = 0.5;

const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);

export class Camera {
  constructor(onMove) {
    this.x = 0;
    this.y = 0;
    this.k = 1;
    this.width = 1;
    this.height = 1;
    this.onMove = onMove;
    this.flight = null;
    this.motionAllowed = !matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  toScreenX(x) {
    return (x - this.x) * this.k + this.width / 2;
  }

  toScreenY(y) {
    return (y - this.y) * this.k + this.height / 2;
  }

  toWorld(sx, sy) {
    return { x: (sx - this.width / 2) / this.k + this.x, y: (sy - this.height / 2) / this.k + this.y };
  }

  visible(node) {
    const r = node.r * this.k;
    const sx = this.toScreenX(node.x);
    const sy = this.toScreenY(node.y);
    return sx + r > 0 && sx - r < this.width && sy + r > 0 && sy - r < this.height;
  }

  framing(node) {
    const room = Math.min(this.width, this.height - 36);
    return { x: node.x, y: node.y, k: room / (2.18 * Math.max(node.r, 0.001)) };
  }

  flyTo(node) {
    const target = this.framing(node);
    if (!this.motionAllowed) {
      Object.assign(this, target);
      this.onMove();
      return;
    }
    this.flight = { start: performance.now(), from: { x: this.x, y: this.y, k: this.k }, to: target };
    this.onMove();
  }

  // Interpolating the inverse of the scale keeps the point being approached steady on screen during the zoom.
  step(now) {
    const flight = this.flight;
    if (!flight) return false;
    const t = Math.min(1, (now - flight.start) / FLIGHT_MILLIS);
    const eased = ease(t);
    const { from, to } = flight;
    this.k = from.k * (to.k / from.k) ** eased;
    const span = 1 / from.k - 1 / to.k;
    const share = Math.abs(span) < 1e-9 ? eased : (1 / from.k - 1 / this.k) / span;
    this.x = from.x + (to.x - from.x) * share;
    this.y = from.y + (to.y - from.y) * share;
    if (t === 1) this.flight = null;
    return true;
  }

  zoomAround(sx, sy, factor, rootRadius) {
    this.flight = null;
    const before = this.toWorld(sx, sy);
    const floor = (Math.min(this.width, this.height) / (2.18 * rootRadius)) * MIN_ZOOM_SHARE;
    this.k = Math.max(floor, this.k * factor);
    const after = this.toWorld(sx, sy);
    this.x += before.x - after.x;
    this.y += before.y - after.y;
    this.onMove();
  }

  panBy(dx, dy) {
    this.flight = null;
    this.x -= dx / this.k;
    this.y -= dy / this.k;
    this.onMove();
  }

  resize(width, height) {
    this.width = width;
    this.height = height;
  }
}
