/// <reference lib="webworker" />

import { CarState, SimulationFrame } from '../../../core/models/car-state.model';
import { CityMapData, RoadSegment } from '../../../core/models/city-map.model';
import { District } from '../../../core/models/district.model';
import { TrafficLight } from '../../../core/models/traffic-light.model';
import { EventObjective } from '../../../core/models/event.model';

/** Espejo del shape de ToolService.SpeedOverrideView -- se evita importar el service (Angular DI) en el worker. */
interface SpeedOverrideView {
  edgeId: string;
  factor: number;
  expiresAtTick: number;
  placedBy: string;
}

let canvas: OffscreenCanvas | null = null;
let ctx: OffscreenCanvasRenderingContext2D | null = null;
let staticLayer: ImageBitmap | null = null;
let mapData: CityMapData | null = null;
let carMap = new Map<string, CarState>();
let animationPending = false;
let worldToCanvas = 1;
let offsetX = 0;
let offsetY = 0;
let viewScale = 1;
let viewPanX = 0;
let viewPanY = 0;

/** Transform "base" (pan=0, scale=1) con la que se construye la capa estatica una sola vez. */
let baseScale = 1;
let baseOffsetX = 0;
let baseOffsetY = 0;

let districts: District[] = [];
let myDistrictIndex = -1;
let blockedEdges: Record<string, string> = {};
let speedOverrides: Record<string, SpeedOverrideView> = {};
let lights: TrafficLight[] = [];
let objective: EventObjective | null = null;
let incoming: { x: number, y: number, expiresAt: number } | null = null;
let shieldActiveUntil = 0;
let pulseTimer: ReturnType<typeof setInterval> | null = null;
const INCOMING_DURATION_MS = 5000;

const DISTRICT_COLORS = [
  '#3d5af1', '#1a936f', '#c1342a', '#e0a80d', '#8b5cf6', '#0891b2'
];

const LIGHT_COLORS: Record<string, string> = {
  GREEN:  '#22c55e',
  YELLOW: '#eab308',
  RED:    '#ef4444'
};

const LIGHT_MIN_SCALE = 2.5;

function districtColor(index: number): string {
  return DISTRICT_COLORS[index % DISTRICT_COLORS.length];
}

addEventListener('message', ({ data }) => {
  switch (data.type) {
    case 'init':
      canvas = data.canvas as OffscreenCanvas;
      ctx = canvas.getContext('2d')!;
      break;
    case 'map':
      mapData = data.map as CityMapData;
      buildStaticLayer();
      scheduleRender();
      break;
    case 'districts':
      districts = (data.districts ?? []) as District[];
      myDistrictIndex = data.myDistrictIndex ?? -1;
      buildStaticLayer();
      scheduleRender();
      break;
    case 'blocked':
      blockedEdges = data.blocked ?? {};
      buildStaticLayer();
      scheduleRender();
      break;
    case 'speedOverrides':
      speedOverrides = data.speedOverrides ?? {};
      syncPulseTimer();
      scheduleRender();
      break;
    case 'incoming':
      incoming = { x: data.x, y: data.y, expiresAt: Date.now() + INCOMING_DURATION_MS };
      syncPulseTimer();
      scheduleRender();
      break;
    case 'shield':
      shieldActiveUntil = data.activeUntil ?? 0;
      syncPulseTimer();
      scheduleRender();
      break;
    case 'lights':
      lights = (data.lights ?? []) as TrafficLight[];
      syncPulseTimer();
      scheduleRender();
      break;
    case 'objective':
      objective = (data.objective ?? null) as EventObjective | null;
      syncPulseTimer();
      scheduleRender();
      break;
    case 'frame':
      applyFrame(data.frame as SimulationFrame);
      scheduleRender();
      break;
    case 'resize':
      if (canvas) {
        canvas.width = data.width;
        canvas.height = data.height;
        buildStaticLayer();
        scheduleRender();
      }
      break;
    case 'clear':
      carMap.clear();
      scheduleRender();
      break;
    case 'pan':
      // Mientras se arrastra/hace zoom, solo se actualiza el transform dinamico
      // y se reposiciona la capa estatica ya construida via ctx transform (barato).
      // Reconstruirla en cada pixel de arrastre es lo que la hacia sentir
      // "pegada". Pero un bitmap escalado se ve borroso si el zoom se aleja
      // mucho del que tenia al construirse -- por eso se agenda un rebuild
      // nitido con debounce, que se dispara solo cuando el usuario deja de
      // mover el mapa (igual que el "retile" de Google Maps/Leaflet).
      viewPanX = data.panX;
      viewPanY = data.panY;
      viewScale = data.scale;
      scheduleRender();
      scheduleCrispRebuild();
      break;
  }
});

function applyFrame(frame: SimulationFrame): void {
  for (const delta of frame.deltas) {
    if (delta.status === 'ARRIVED') {
      carMap.delete(delta.carId);
    } else {
      carMap.set(delta.carId, {
        carId: delta.carId,
        x: delta.x,
        y: delta.y,
        heading: delta.heading,
        status: delta.status,
        color: delta.color
      });
    }
  }
  for (const id of frame.removedCarIds) {
    carMap.delete(id);
  }
}

function scheduleRender(): void {
  if (!animationPending) {
    animationPending = true;
    setTimeout(render, 0);
  }
}

let crispRebuildTimer: ReturnType<typeof setTimeout> | null = null;

/** Reconstruye la capa estatica nitida ~180ms despues del ultimo pan/zoom (cuando el usuario se detiene). */
function scheduleCrispRebuild(): void {
  if (crispRebuildTimer) clearTimeout(crispRebuildTimer);
  crispRebuildTimer = setTimeout(() => {
    crispRebuildTimer = null;
    buildStaticLayer();
    scheduleRender();
  }, 180);
}

/**
 * El objetivo activo parpadea, asi que hace falta repintar aunque no lleguen
 * frames nuevos. Se hace con un setInterval independiente, NO reagendando
 * render() desde dentro de si misma: ese patron dejo animationPending
 * trabado en true y congelo la capa dinamica.
 */
function syncPulseTimer(): void {
  const needsPulse = objective !== null || lights.some(l => l.forced)
      || Object.keys(speedOverrides).length > 0 || incoming !== null
      || Date.now() < shieldActiveUntil;
  if (needsPulse && !pulseTimer) {
    pulseTimer = setInterval(scheduleRender, 60);
  } else if (!needsPulse && pulseTimer) {
    clearInterval(pulseTimer);
    pulseTimer = null;
  }
}

function isBlocked(seg: RoadSegment): boolean {
  return seg.id in blockedEdges || (seg.id + '_R') in blockedEdges;
}

function findSegment(edgeId: string): RoadSegment | null {
  if (!mapData) return null;
  const base = edgeId.endsWith('_R') ? edgeId.slice(0, -2) : edgeId;
  for (const s of mapData.highways) if (s.id === base) return s;
  for (const s of mapData.streets)  if (s.id === base) return s;
  return null;
}

function districtBounds(d: District): { x: number, y: number, w: number, h: number } | null {
  if (!mapData) return null;
  const zoneIds = new Set(d.zoneIds);
  const zones = mapData.zones.filter(z => zoneIds.has(z.id));
  if (zones.length === 0) return null;

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const z of zones) {
    minX = Math.min(minX, z.x);
    minY = Math.min(minY, z.y);
    maxX = Math.max(maxX, z.x + z.w);
    maxY = Math.max(maxY, z.y + z.h);
  }
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}

function drawSegments(sCtx: OffscreenCanvasRenderingContext2D,
                      segs: RoadSegment[], color: string, worldWidth: number): void {
  sCtx.strokeStyle = color;
  sCtx.lineWidth = Math.max(1, worldWidth * worldToCanvas);
  sCtx.beginPath();
  for (const s of segs) {
    if (isBlocked(s)) continue;
    sCtx.moveTo(worldX(s.x1), worldY(s.y1));
    sCtx.lineTo(worldX(s.x2), worldY(s.y2));
  }
  sCtx.stroke();
}

function drawBlocked(sCtx: OffscreenCanvasRenderingContext2D,
                     segs: RoadSegment[], worldWidth: number): void {
  const blocked = segs.filter(isBlocked);
  if (blocked.length === 0) return;

  sCtx.strokeStyle = '#c1342a';
  sCtx.lineWidth = Math.max(2, worldWidth * worldToCanvas);
  sCtx.beginPath();
  for (const s of blocked) {
    sCtx.moveTo(worldX(s.x1), worldY(s.y1));
    sCtx.lineTo(worldX(s.x2), worldY(s.y2));
  }
  sCtx.stroke();

  sCtx.strokeStyle = '#ff7b72';
  sCtx.lineWidth = 2;
  sCtx.beginPath();
  for (const s of blocked) {
    const mx = worldX((s.x1 + s.x2) / 2);
    const my = worldY((s.y1 + s.y2) / 2);
    const r = Math.max(3, worldToCanvas * 2);
    sCtx.moveTo(mx - r, my - r); sCtx.lineTo(mx + r, my + r);
    sCtx.moveTo(mx + r, my - r); sCtx.lineTo(mx - r, my + r);
  }
  sCtx.stroke();
}

function buildStaticLayer(): void {
  if (!canvas || !ctx || !mapData) return;

  const w = canvas.width;
  const h = canvas.height;

  // Se construye con el pan/zoom ACTUAL (no uno fijo): asi queda nitida en
  // el momento de construirse. render() la reposiciona con un ctx transform
  // barato mientras el usuario sigue moviendo el mapa (pan=0 rebuild costaria
  // demasiado por evento); en cuanto se detiene, scheduleCrispRebuild()
  // llama otra vez a esta funcion y "snapea" la nitidez a la posicion final.
  computeTransform(w, h);
  baseScale = worldToCanvas; baseOffsetX = offsetX; baseOffsetY = offsetY;

  const offscreen = new OffscreenCanvas(w, h);
  const sCtx = offscreen.getContext('2d')!;

  sCtx.fillStyle = '#1a1a2e';
  sCtx.fillRect(0, 0, w, h);

  for (const d of districts) {
    const b = districtBounds(d);
    if (!b) continue;

    const x1 = worldX(b.x);
    const y1 = worldY(b.y);
    const dw = b.w * worldToCanvas;
    const dh = b.h * worldToCanvas;
    const color = districtColor(d.index);
    const isMine = d.index === myDistrictIndex;

    sCtx.globalAlpha = isMine ? 0.38 : 0.20;
    sCtx.fillStyle = color;
    sCtx.fillRect(x1, y1, dw, dh);

    sCtx.globalAlpha = isMine ? 1.0 : 0.6;
    sCtx.strokeStyle = color;
    sCtx.lineWidth = isMine ? 5 : 2;
    sCtx.strokeRect(x1, y1, dw, dh);

    sCtx.globalAlpha = 1.0;
  }

  drawSegments(sCtx, mapData.streets, '#2d3561', 2.6);
  drawSegments(sCtx, mapData.highways, '#4a5080', 4.6);

  drawBlocked(sCtx, mapData.streets, 2.6);
  drawBlocked(sCtx, mapData.highways, 4.6);

  staticLayer = offscreen.transferToImageBitmap();
}

/** El color va de verde (bien) a rojo (mal) segun que tan cerca esta del limite. */
function urgencyColor(ratio: number): string {
  const clamped = Math.max(0, Math.min(1, ratio));
  if (clamped > 0.5) return '#22c55e';
  if (clamped > 0.2) return '#eab308';
  return '#ef4444';
}

/**
 * El objetivo activo del administrador. Va en la capa dinamica porque
 * parpadea: es lo que guia la accion del usuario.
 */
function drawObjective(c: OffscreenCanvasRenderingContext2D): void {
  if (!objective) return;
  switch (objective.kind) {
    case 'CLOSE_EDGE':      drawTarget(c, objective.edgeIds[0]); break;
    case 'SHIELD_AREA':     drawArea(c, objective, objective.current / objective.threshold, false); break;
    case 'EVACUATE_AREA':   drawArea(c, objective, 1 - objective.current / Math.max(1, objective.threshold * 2), true); break;
    case 'CLEAR_CORRIDOR':  drawCorridor(c, objective); break;
    case 'RELIEVE_JUNCTION': drawJunction(c, objective); break;
  }
}

function drawTarget(c: OffscreenCanvasRenderingContext2D, targetEdge: string | undefined): void {
  if (!targetEdge) return;
  const s = findSegment(targetEdge);
  if (!s) return;

  // Parpadeo suave (periodo de 1.2 s)
  const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);

  const x1 = worldX(s.x1), y1 = worldY(s.y1);
  const x2 = worldX(s.x2), y2 = worldY(s.y2);

  // Halo exterior
  c.globalAlpha = 0.25 * pulse;
  c.strokeStyle = '#e0a80d';
  c.lineWidth = Math.max(8, 12 * worldToCanvas);
  c.lineCap = 'round';
  c.beginPath();
  c.moveTo(x1, y1); c.lineTo(x2, y2);
  c.stroke();

  // Trazo naranja
  c.globalAlpha = pulse;
  c.strokeStyle = '#f59e0b';
  c.lineWidth = Math.max(3, 5 * worldToCanvas);
  c.beginPath();
  c.moveTo(x1, y1); c.lineTo(x2, y2);
  c.stroke();

  // Marcador en el centro
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  const r = Math.max(6, worldToCanvas * 4);

  c.globalAlpha = 1.0;
  c.fillStyle = '#f59e0b';
  c.beginPath();
  c.arc(mx, my, r, 0, Math.PI * 2);
  c.fill();

  c.strokeStyle = '#0d0f1e';
  c.lineWidth = 2;
  c.stroke();

  // Signo de exclamacion
  c.fillStyle = '#0d0f1e';
  c.font = `700 ${Math.max(9, r * 1.3)}px monospace`;
  c.textAlign = 'center';
  c.textBaseline = 'middle';
  c.fillText('!', mx, my + 1);

  c.globalAlpha = 1.0;
  c.lineCap = 'butt';
}

/** SHIELD_AREA / EVACUATE_AREA: rectangulo translucido con borde punteado. */
function drawArea(c: OffscreenCanvasRenderingContext2D, obj: EventObjective,
                  goodRatio: number, showCount: boolean): void {
  if (obj.minX == null || obj.minY == null || obj.maxX == null || obj.maxY == null) return;

  const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);
  const color = urgencyColor(goodRatio);

  const x1 = worldX(obj.minX), y1 = worldY(obj.minY);
  const x2 = worldX(obj.maxX), y2 = worldY(obj.maxY);
  const w = x2 - x1, h = y2 - y1;

  c.globalAlpha = 0.18 * pulse + 0.1;
  c.fillStyle = color;
  c.fillRect(x1, y1, w, h);

  c.globalAlpha = 0.85;
  c.strokeStyle = color;
  c.lineWidth = Math.max(2, 3 * worldToCanvas);
  c.setLineDash([Math.max(4, 6 * worldToCanvas), Math.max(3, 4 * worldToCanvas)]);
  c.strokeRect(x1, y1, w, h);
  c.setLineDash([]);

  const label = showCount ? String(obj.current) : String(obj.current);
  c.globalAlpha = 1.0;
  c.fillStyle = color;
  c.font = `700 ${Math.max(12, Math.min(w, h) * 0.3)}px monospace`;
  c.textAlign = 'center';
  c.textBaseline = 'middle';
  c.fillText(label, x1 + w / 2, y1 + h / 2);
}

/** CLEAR_CORRIDOR: la cadena de tramos resaltada, verde si esta limpia, roja si hay un carro. */
function drawCorridor(c: OffscreenCanvasRenderingContext2D, obj: EventObjective): void {
  if (!mapData || obj.edgeIds.length === 0) return;

  const color = obj.current === 0 ? '#ef4444' : '#22c55e';
  const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);

  c.globalAlpha = 0.3 * pulse + 0.5;
  c.strokeStyle = color;
  c.lineWidth = Math.max(5, 7 * worldToCanvas);
  c.lineCap = 'round';
  c.beginPath();
  for (const edgeId of obj.edgeIds) {
    const s = findSegment(edgeId);
    if (!s) continue;
    c.moveTo(worldX(s.x1), worldY(s.y1));
    c.lineTo(worldX(s.x2), worldY(s.y2));
  }
  c.stroke();
  c.lineCap = 'butt';

  const mid = findSegment(obj.edgeIds[Math.floor(obj.edgeIds.length / 2)]);
  if (mid) {
    const mx = worldX((mid.x1 + mid.x2) / 2);
    const my = worldY((mid.y1 + mid.y2) / 2);
    c.globalAlpha = 1.0;
    c.fillStyle = color;
    c.font = `700 ${Math.max(10, worldToCanvas * 5)}px monospace`;
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    c.fillText(`${obj.current}s/${obj.threshold}s`, mx, my - 10);
  }
  c.globalAlpha = 1.0;
}

/** Coordenadas de mundo de un nodo a partir de su id "N_<row>_<col>" (BLOCK_SIZE=10). */
function nodeWorldPos(nodeId: string): { x: number, y: number } | null {
  const parts = nodeId.split('_');
  if (parts.length !== 3 || parts[0] !== 'N') return null;
  const row = Number(parts[1]);
  const col = Number(parts[2]);
  if (Number.isNaN(row) || Number.isNaN(col)) return null;
  return { x: col * 10, y: row * 10 };
}

/** RELIEVE_JUNCTION: circulo alrededor del cruce con la cifra de cola. */
function drawJunction(c: OffscreenCanvasRenderingContext2D, obj: EventObjective): void {
  if (!mapData || !obj.intersectionId) return;
  const pos = nodeWorldPos(obj.intersectionId);
  if (!pos) return;

  const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);
  const ratio = 1 - obj.current / Math.max(1, obj.threshold);
  const color = urgencyColor(ratio);

  const cx = worldX(pos.x), cy = worldY(pos.y);
  const r = Math.max(14, 18 * worldToCanvas);

  c.globalAlpha = 0.2 * pulse + 0.15;
  c.fillStyle = color;
  c.beginPath();
  c.arc(cx, cy, r, 0, Math.PI * 2);
  c.fill();

  c.globalAlpha = 0.9;
  c.strokeStyle = color;
  c.lineWidth = Math.max(2, 3 * worldToCanvas);
  c.beginPath();
  c.arc(cx, cy, r, 0, Math.PI * 2);
  c.stroke();

  c.globalAlpha = 1.0;
  c.fillStyle = color;
  c.font = `700 ${Math.max(11, r * 0.5)}px monospace`;
  c.textAlign = 'center';
  c.textBaseline = 'middle';
  c.fillText(`${obj.current}/${obj.threshold}`, cx, cy);
}

/** REDUCTOR/TURBO activos. Va en la capa dinamica: el turbo anima flechas. */
function drawSpeedOverrides(c: OffscreenCanvasRenderingContext2D): void {
  if (!mapData) return;
  const seen = new Set<string>();
  for (const key in speedOverrides) {
    const override = speedOverrides[key];
    const base = override.edgeId.endsWith('_R') ? override.edgeId.slice(0, -2) : override.edgeId;
    if (seen.has(base)) continue;
    seen.add(base);

    const s = findSegment(base);
    if (!s) continue;

    if (override.factor < 1) drawSpeedTrap(c, s);
    else drawSpeedBoost(c, s);
  }
}

/** REDUCTOR: franjas diagonales amarillo/negro, como una cinta de peligro sobre la via. */
function drawSpeedTrap(c: OffscreenCanvasRenderingContext2D, s: RoadSegment): void {
  const x1 = worldX(s.x1), y1 = worldY(s.y1);
  const x2 = worldX(s.x2), y2 = worldY(s.y2);
  const dx = x2 - x1, dy = y2 - y1;
  const len = Math.hypot(dx, dy);
  if (len < 1) return;

  const ux = dx / len, uy = dy / len;
  const px = -uy, py = ux;
  const halfStripe = Math.max(4, 5 * worldToCanvas);
  const step = Math.max(7, 9 * worldToCanvas);
  const dirx = (ux + px) * Math.SQRT1_2;
  const diry = (uy + py) * Math.SQRT1_2;

  c.lineWidth = Math.max(2, 2.6 * worldToCanvas);
  c.lineCap = 'butt';
  let toggle = false;
  for (let d = 0; d <= len; d += step) {
    const cx = x1 + ux * d, cy = y1 + uy * d;
    c.strokeStyle = toggle ? '#eab308' : '#0d0f1e';
    toggle = !toggle;
    c.beginPath();
    c.moveTo(cx - dirx * halfStripe, cy - diry * halfStripe);
    c.lineTo(cx + dirx * halfStripe, cy + diry * halfStripe);
    c.stroke();
  }
}

/** TURBO: brillo azul pulsante con flechas animadas en el sentido de la via. */
function drawSpeedBoost(c: OffscreenCanvasRenderingContext2D, s: RoadSegment): void {
  const x1 = worldX(s.x1), y1 = worldY(s.y1);
  const x2 = worldX(s.x2), y2 = worldY(s.y2);
  const dx = x2 - x1, dy = y2 - y1;
  const len = Math.hypot(dx, dy);
  if (len < 1) return;

  const pulse = 0.5 + 0.5 * Math.sin(Date.now() / 190);

  c.globalAlpha = 0.22 * pulse + 0.13;
  c.strokeStyle = '#38bdf8';
  c.lineWidth = Math.max(6, 9 * worldToCanvas);
  c.lineCap = 'round';
  c.beginPath();
  c.moveTo(x1, y1); c.lineTo(x2, y2);
  c.stroke();
  c.lineCap = 'butt';
  c.globalAlpha = 1.0;

  const ux = dx / len, uy = dy / len;
  const angle = Math.atan2(dy, dx);
  const spacing = Math.max(16, 20 * worldToCanvas);
  const speedPxPerSec = 55;
  const offset = (Date.now() / 1000 * speedPxPerSec) % spacing;
  const arrowSize = Math.max(3, 4 * worldToCanvas);

  c.fillStyle = '#38bdf8';
  c.globalAlpha = 0.9;
  for (let d = offset; d < len; d += spacing) {
    const cx = x1 + ux * d, cy = y1 + uy * d;
    drawArrowHead(c, cx, cy, angle, arrowSize);
  }
  c.globalAlpha = 1.0;
}

function drawArrowHead(c: OffscreenCanvasRenderingContext2D,
                       cx: number, cy: number, angle: number, size: number): void {
  c.save();
  c.translate(cx, cy);
  c.rotate(angle);
  c.beginPath();
  c.moveTo(size, 0);
  c.lineTo(-size * 0.6, size * 0.6);
  c.lineTo(-size * 0.6, -size * 0.6);
  c.closePath();
  c.fill();
  c.restore();
}

/** ESCUDO DE DISTRITO: borde dorado pulsante alrededor de mi propio distrito mientras esta activo. */
function drawShield(c: OffscreenCanvasRenderingContext2D): void {
  if (Date.now() >= shieldActiveUntil) return;
  const mine = districts.find(d => d.index === myDistrictIndex);
  if (!mine) return;
  const b = districtBounds(mine);
  if (!b) return;

  const pulse = 0.5 + 0.5 * Math.sin(Date.now() / 190);
  const x1 = worldX(b.x), y1 = worldY(b.y);
  const w = b.w * worldToCanvas, h = b.h * worldToCanvas;

  c.globalAlpha = 0.5 * pulse + 0.4;
  c.strokeStyle = '#eab308';
  c.lineWidth = Math.max(4, 7 * worldToCanvas);
  c.strokeRect(x1, y1, w, h);
  c.globalAlpha = 1.0;
}

/** LLUVIA DE TRAFICO: anillo rojo tipo radar en el punto elegido, 5s. */
function drawIncoming(c: OffscreenCanvasRenderingContext2D): void {
  if (!incoming) return;

  const cx = worldX(incoming.x), cy = worldY(incoming.y);
  const elapsed = INCOMING_DURATION_MS - (incoming.expiresAt - Date.now());
  const ringPeriod = 900;
  const cycle = (elapsed % ringPeriod) / ringPeriod;
  const maxR = Math.max(20, 26 * worldToCanvas);

  c.globalAlpha = Math.max(0, 1 - cycle) * 0.8;
  c.strokeStyle = '#ef4444';
  c.lineWidth = Math.max(2, 3 * worldToCanvas);
  c.beginPath();
  c.arc(cx, cy, cycle * maxR, 0, Math.PI * 2);
  c.stroke();

  c.globalAlpha = 0.9;
  c.fillStyle = '#ef4444';
  c.beginPath();
  c.arc(cx, cy, Math.max(4, 5 * worldToCanvas), 0, Math.PI * 2);
  c.fill();
  c.globalAlpha = 1.0;
}

/**
 * Cuatro luces por cruce: dos para el eje horizontal (este y oeste) y dos para
 * el vertical (norte y sur). Los ejes estan siempre en oposicion, asi que si
 * las horizontales estan verdes, las verticales estan rojas.
 */
function drawLights(c: OffscreenCanvasRenderingContext2D): void {
  if (viewScale < LIGHT_MIN_SCALE || lights.length === 0) return;

  const r = Math.max(2, worldToCanvas * 0.8);
  const d = Math.max(5, worldToCanvas * 2.2);   // separacion desde el centro

  for (const l of lights) {
    const cx = worldX(l.x);
    const cy = worldY(l.y);

    const hColor = LIGHT_COLORS[l.horizontalState] ?? '#94a3b8';
    const vColor = LIGHT_COLORS[l.verticalState] ?? '#94a3b8';

    if (l.forced) drawForcedHalo(c, cx, cy, d);

    // Eje horizontal: luces a izquierda y derecha del cruce
    dot(c, cx - d, cy, r, hColor);
    dot(c, cx + d, cy, r, hColor);

    // Eje vertical: luces arriba y abajo
    dot(c, cx, cy - d, r, vColor);
    dot(c, cx, cy + d, r, vColor);
  }

  c.globalAlpha = 1.0;
}

/** FORCE_GREEN: halo verde pulsante, mas grande que las luces normales, sobre el cruce forzado. */
function drawForcedHalo(c: OffscreenCanvasRenderingContext2D, cx: number, cy: number, d: number): void {
  const pulse = 0.5 + 0.5 * Math.sin(Date.now() / 190);
  const haloR = Math.max(10, d * 2.2);

  c.globalAlpha = 0.18 * pulse + 0.1;
  c.fillStyle = '#22c55e';
  c.beginPath();
  c.arc(cx, cy, haloR, 0, Math.PI * 2);
  c.fill();

  c.globalAlpha = 0.7 * pulse + 0.3;
  c.strokeStyle = '#22c55e';
  c.lineWidth = Math.max(2, 3 * worldToCanvas);
  c.beginPath();
  c.arc(cx, cy, haloR, 0, Math.PI * 2);
  c.stroke();

  c.globalAlpha = 1.0;
}

function dot(c: OffscreenCanvasRenderingContext2D,
             x: number, y: number, r: number, color: string): void {
  c.globalAlpha = 0.3;
  c.fillStyle = color;
  c.beginPath();
  c.arc(x, y, r * 1.9, 0, Math.PI * 2);
  c.fill();

  c.globalAlpha = 1.0;
  c.fillStyle = color;
  c.beginPath();
  c.arc(x, y, r, 0, Math.PI * 2);
  c.fill();

  c.strokeStyle = 'rgba(10,12,30,0.85)';
  c.lineWidth = 1;
  c.stroke();
}

function render(): void {
  animationPending = false;
  if (!canvas || !ctx || !mapData) return;

  if (incoming && Date.now() > incoming.expiresAt) {
    incoming = null;
    syncPulseTimer();
  }

  const w = canvas.width;
  const h = canvas.height;

  computeTransform(w, h);

  ctx.clearRect(0, 0, w, h);
  if (staticLayer && baseScale > 0) {
    // La capa estatica se construyo una sola vez con el transform base
    // (pan=0/scale=1); aqui se reposiciona con un ctx transform barato para
    // reflejar el pan/zoom actual, sin reconstruir el mapa completo.
    const k = worldToCanvas / baseScale;
    ctx.save();
    ctx.setTransform(k, 0, 0, k, offsetX - baseOffsetX * k, offsetY - baseOffsetY * k);
    ctx.drawImage(staticLayer, 0, 0);
    ctx.restore();
  } else {
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(0, 0, w, h);
  }

  drawObjective(ctx);
  drawSpeedOverrides(ctx);
  drawIncoming(ctx);
  drawShield(ctx);
  drawLights(ctx);

  const carSize = Math.max(1.5, worldToCanvas * 0.9);
  for (const car of carMap.values()) {
    drawCar(ctx, car, carSize);
  }
}

function drawCar(c: OffscreenCanvasRenderingContext2D, car: CarState, size: number): void {
  const cx = worldX(car.x);
  const cy = worldY(car.y);

  c.save();
  c.translate(cx, cy);
  c.rotate(car.heading);

  c.fillStyle = car.color;
  c.globalAlpha = car.status === 'WAITING_LIGHT' ? 0.6 :
                  car.status === 'WAITING_TRAFFIC' ? 0.7 : 1.0;
  c.fillRect(-size, -size * 0.5, size * 2, size);

  c.fillStyle = '#ffffff';
  c.globalAlpha = 0.9;
  c.fillRect(size * 0.7, -size * 0.15, size * 0.3, size * 0.3);

  c.globalAlpha = 1.0;
  c.restore();
}

function computeTransform(w: number, h: number): void {
  if (!mapData) return;
  const scaleX = w / mapData.width;
  const scaleY = h / mapData.height;
  worldToCanvas = Math.min(scaleX, scaleY) * viewScale;
  offsetX = (w - mapData.width * worldToCanvas) / 2 + viewPanX;
  offsetY = (h - mapData.height * worldToCanvas) / 2 + viewPanY;
}

function worldX(wx: number): number {
  return wx * worldToCanvas + offsetX;
}

function worldY(wy: number): number {
  return wy * worldToCanvas + offsetY;
}
