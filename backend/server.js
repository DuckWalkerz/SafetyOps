const express = require('express');
const http = require('http');
const fs = require('fs');
const path = require('path');

const app = express();
const server = http.createServer(app);
const PORT = process.env.PORT || 9090;

app.use(express.json());
app.use((req, res, next) => { res.header("Access-Control-Allow-Origin", "*"); res.header("Access-Control-Allow-Headers", "Content-Type"); next(); });

const frames = new Map(), locations = new Map(), devices = new Map(), sse = new Set(), chat = [];
const B = 'frame';

app.post('/location', (req, res) => {
  const { lat, lon, acc, battery, platform, speed, bearing, alt } = req.body;
  const device = req.query.device || 'demo';
  locations.set(device, { lat: +lat, lon: +lon, acc: +acc, battery: +battery, speed: +speed, bearing: +bearing, alt: +alt, t: Date.now() });
  if (!devices.has(device)) { devices.set(device, { id: device, platform: platform || 'unknown', frames: 0, lastSeen: Date.now() }); broadcast('device_new', devices.get(device)); }
  devices.get(device).lastSeen = Date.now();
  broadcast('location', { device, ...locations.get(device) });
  res.json({ ok: true });
});

app.post('/frame', express.raw({ type: 'image/jpeg', limit: '5mb' }), (req, res) => {
  const device = req.query.device || 'demo';
  if (req.body.length) {
    frames.set(device, req.body);
    if (devices.has(device)) devices.get(device).frames++;
    if (streamSubs.has(device)) { for (const r of streamSubs.get(device)) { try { r.write(`--${B}\r\nContent-Type: image/jpeg\r\nContent-Length: ${req.body.length}\r\n\r\n`); r.write(req.body); r.write('\r\n'); } catch(e){} } }
  }
  res.json({ ok: true });
});

const streamSubs = new Map();
app.get('/stream/:device', (req, res) => {
  const d = req.params.device;
  res.writeHead(200, { 'Content-Type': `multipart/x-mixed-replace; boundary=${B}`, 'Cache-Control': 'no-store' });
  if (!streamSubs.has(d)) streamSubs.set(d, new Set());
  streamSubs.get(d).add(res);
  if (frames.has(d)) { res.write(`--${B}\r\nContent-Type: image/jpeg\r\nContent-Length: ${frames.get(d).length}\r\n\r\n`); res.write(frames.get(d)); res.write('\r\n'); }
  req.on('close', () => streamSubs.get(d)?.delete(res));
});

function broadcast(event, data) {
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const res of [...sse]) { try { res.write(payload); } catch { sse.delete(res); } }
}

app.get('/events', (req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'Connection': 'keep-alive' });
  res.write('retry: 2000\n\n');
  res.write(`event: snapshot\ndata: ${JSON.stringify({ devices: [...devices.values()], locations: Object.fromEntries(locations) })}\n\n`);
  sse.add(res);
  req.on('close', () => sse.delete(res));
});

app.get('/api/chat', (req, res) => res.json(chat));
app.post('/api/chat', (req, res) => {
  const msg = { id: Date.now(), from: req.body.from, text: req.body.text, time: Date.now() };
  chat.push(msg); if (chat.length > 100) chat.shift();
  broadcast('chat', msg); res.json({ ok: true });
});

app.use(express.static(path.join(__dirname, 'public')));
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'hq.html')));

server.listen(PORT, () => console.log(`Command Center on :${PORT}`));
