'use strict';

const http = require('http');
const fs   = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT     = parseInt(process.env.PORT || '9090', 10);
const BOUNDARY = 'frame';
const MAX_BODY = 4 * 1024 * 1024;
const STALE_MS = 30000;

const frames     = new Map();
const locations  = new Map();
const devices    = new Map();
const sse        = new Set();
const streamSubs = new Map();

const HQ_HTML = fs.readFileSync(path.join(__dirname, 'hq.html'), 'utf-8');

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

function stamp() { return new Date().toISOString(); }
function log()   { console.log.apply(console, [stamp()].concat(Array.prototype.slice.call(arguments))); }

function json(res, code, obj) {
  res.writeHead(code, Object.assign({}, CORS, { 'Content-Type': 'application/json' }));
  res.end(JSON.stringify(obj));
}

function broadcast(event, data) {
  const payload = 'event: ' + event + '\ndata: ' + JSON.stringify(data) + '\n\n';
  sse.forEach(function (res) {
    try { res.write(payload); } catch (e) { sse.delete(res); }
  });
}

function writeMjpeg(res, buf) {
  res.write('--' + BOUNDARY + '\r\n');
  res.write('Content-Type: image/jpeg\r\n');
  res.write('Content-Length: ' + buf.length + '\r\n\r\n');
  res.write(buf);
  res.write('\r\n');
}

function touchDevice(id, platform) {
  let d = devices.get(id);
  if (!d) {
    d = { id: id, firstSeen: Date.now(), lastSeen: Date.now(), frames: 0, platform: platform || 'unknown' };
    devices.set(id, d);
    broadcast('device_new', d);
  }
  d.lastSeen = Date.now();
  if (platform && platform !== 'unknown') d.platform = platform;
  return d;
}

function pushFrame(device, buf) {
  frames.set(device, buf);
  const subs = streamSubs.get(device);
  if (!subs) return;
  Array.from(subs).forEach(function (res) {
    try { writeMjpeg(res, buf); } catch (e) { subs.delete(res); }
  });
}

function readBody(req) {
  return new Promise(function (resolve, reject) {
    const chunks = [];
    let size = 0;
    req.on('data', function (c) {
      size += c.length;
      if (size > MAX_BODY) { reject(new Error('body too large')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end',   function () { resolve(Buffer.concat(chunks)); });
    req.on('error', reject);
  });
}

const server = http.createServer(async function (req, res) {
  const url = new URL(req.url || '/', 'http://' + (req.headers.host || 'x'));
  const pathname = url.pathname;
  const device   = url.searchParams.get('device') || 'demo';

  if (req.method === 'OPTIONS') { res.writeHead(204, CORS); res.end(); return; }

  try {
    if (req.method === 'POST' && pathname === '/location') {
      const body = await readBody(req);
      let j;
      try { j = JSON.parse(body.toString()); } catch (e) { return json(res, 400, { error: 'bad json' }); }
      const lat = +j.lat, lon = +j.lon;
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return json(res, 400, { error: 'bad coords' });
      const loc = {
        lat: lat,
        lon: lon,
        acc: +(j.acc || 0),
        battery: j.battery != null ? +j.battery : null,
        speed: j.speed != null ? +j.speed : 0,
        bearing: j.bearing != null ? +j.bearing : 0,
        alt: j.alt != null ? +j.alt : 0,
        t: Date.now(),
      };
      locations.set(device, loc);
      touchDevice(device, j.platform);
      broadcast('location', Object.assign({ device: device }, loc));
      return json(res, 200, { ok: true });
    }

    if (req.method === 'POST' && pathname === '/frame') {
      const body = await readBody(req);
      if (!body.length) return json(res, 400, { error: 'empty' });
      pushFrame(device, body);
      const d = touchDevice(device);
      d.frames = (d.frames || 0) + 1;
      return json(res, 200, { ok: true });
    }

    if (pathname.indexOf('/stream/') === 0) {
      const id = decodeURIComponent(pathname.slice('/stream/'.length));
      res.writeHead(200, Object.assign({}, CORS, {
        'Content-Type': 'multipart/x-mixed-replace; boundary=' + BOUNDARY,
        'Cache-Control': 'no-store, no-cache, must-revalidate',
        'Pragma': 'no-cache',
        'Connection': 'close',
        'X-Accel-Buffering': 'no',
      }));
      const last = frames.get(id);
      if (last) writeMjpeg(res, last);
      if (!streamSubs.has(id)) streamSubs.set(id, new Set());
      streamSubs.get(id).add(res);
      req.on('close', function () {
        const set = streamSubs.get(id);
        if (set) set.delete(res);
      });
      return;
    }

    if (pathname === '/events') {
      res.writeHead(200, Object.assign({}, CORS, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-transform',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no',
      }));
      res.write('retry: 3000\n\n');
      const snap = {
        devices: Array.from(devices.values()),
        locations: Object.fromEntries(locations),
      };
      res.write('event: snapshot\ndata: ' + JSON.stringify(snap) + '\n\n');
      sse.add(res);
      const keep = setInterval(function () {
        try { res.write(': keep-alive\n\n'); } catch (e) { clearInterval(keep); sse.delete(res); }
      }, 20000);
      req.on('close', function () { clearInterval(keep); sse.delete(res); });
      return;
    }

    if (pathname === '/devices') {
      return json(res, 200, {
        devices: Array.from(devices.values()),
        locations: Object.fromEntries(locations),
      });
    }

    if (pathname === '/health') {
      const totalFrames = Array.from(devices.values()).reduce(function (s, d) { return s + (d.frames || 0); }, 0);
      return json(res, 200, { ok: true, uptime: Math.round(process.uptime()), devices: devices.size, frames: totalFrames });
    }

    if (pathname === '/' || pathname === '/hq') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(HQ_HTML);
      return;
    }

    res.writeHead(404, CORS);
    res.end('not found');
  } catch (e) {
    log('error', req.method, pathname, e.message);
    try { json(res, 500, { error: e.message }); } catch (e2) {}
  }
});

server.listen(PORT, '0.0.0.0', function () {
  log('listening on :' + PORT);
  log('dashboard  http://localhost:' + PORT + '/');
  log('health     http://localhost:' + PORT + '/health');
});

process.on('SIGINT',  function () { log('shutdown'); server.close(function () { process.exit(0); }); });
process.on('SIGTERM', function () { log('shutdown'); server.close(function () { process.exit(0); }); });

setInterval(function () {
  const now = Date.now();
  devices.forEach(function (d, id) { if (now - d.lastSeen > 3600000) devices.delete(id); });
  locations.forEach(function (l, id) { if (now - l.t > 3600000) locations.delete(id); });
}, 60000);

setInterval(function () {
  const now = Date.now();
  const stale = Array.from(devices.values()).filter(function (d) { return now - d.lastSeen > STALE_MS; });
  if (stale.length) broadcast('stale', { devices: stale.map(function (d) { return d.id; }), t: now });
}, 10000);
