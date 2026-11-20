"""Apk-Runner dashboard: a tiny local web UI (standard library only).

Drop APKs on it to install them; installed apps appear as tiles you can open or remove.
It listens on 127.0.0.1 only, and every request must carry a random per-run token, so
other websites in your browser can't drive it.
"""
import http.server
import json
import os
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import webbrowser
from urllib.parse import parse_qs, quote, urlparse

from . import __version__, backends, macapp

TOKEN = secrets.token_urlsafe(16)
RUNNING = {}  # package -> Popen
LOCK = threading.Lock()
MAX_UPLOAD = 4 << 30
EXTS = (".apk", ".xapk", ".apkm", ".apks")


def _apps():
    out = []
    for pkg, e in sorted(macapp.registry().items(), key=lambda x: x[1]["label"].lower()):
        p = RUNNING.get(pkg)
        out.append({"package": pkg, "label": e["label"],
                    "orientation": e.get("override") or e.get("orientation") or "auto",
                    "icon": os.path.exists(os.path.join(macapp.ICONS, pkg + ".img")),
                    "running": bool(p and p.poll() is None)})
    return out


def _runtime():
    b = backends.pick()
    return {"backend": b.name if b else None, "version": __version__}


def _launch(pkg):
    with LOCK:
        p = RUNNING.get(pkg)
        if p and p.poll() is None:
            return
        RUNNING[pkg] = subprocess.Popen(macapp.self_command() + ["--no-update-check", "launch", pkg],
                                        stdin=subprocess.DEVNULL, start_new_session=True)


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "apkrunner"

    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype="application/json"):
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def _authed(self, query):
        given = self.headers.get("X-Token") or query.get("t", [""])[0]
        return secrets.compare_digest(given, TOKEN)

    def do_GET(self):
        url = urlparse(self.path)
        q = parse_qs(url.query)
        if url.path == "/":
            if not self._authed(q):
                return self._send(403, b"Open the dashboard with: apkrunner ui", "text/plain")
            return self._send(200, PAGE.encode(), "text/html; charset=utf-8")
        if not self._authed(q):
            return self._send(403, {"error": "forbidden"})
        if url.path == "/api/apps":
            return self._send(200, {"apps": _apps(), "runtime": _runtime()})
        if url.path == "/api/icon":
            pkg = q.get("pkg", [""])[0]
            if pkg in macapp.registry():
                try:
                    with open(os.path.join(macapp.ICONS, pkg + ".img"), "rb") as f:
                        data = f.read()
                    ctype = "image/webp" if data[8:12] == b"WEBP" else "image/png"
                    return self._send(200, data, ctype)
                except OSError:
                    pass
            return self._send(404, {"error": "no icon"})
        self._send(404, {"error": "not found"})

    def do_POST(self):
        url = urlparse(self.path)
        q = parse_qs(url.query)
        if not self._authed(q):
            return self._send(403, {"error": "forbidden"})
        try:
            if url.path == "/api/install":
                return self._install(q)
            pkg = q.get("pkg", [""])[0]
            if pkg not in macapp.registry():
                return self._send(404, {"error": "unknown app"})
            if url.path == "/api/open":
                _launch(pkg)
            elif url.path == "/api/remove":
                p = RUNNING.pop(pkg, None)
                if p and p.poll() is None:
                    p.terminate()
                macapp.uninstall(pkg)
            elif url.path == "/api/rotate":
                macapp.rotate(pkg, q.get("side", ["auto"])[0])
            else:
                return self._send(404, {"error": "not found"})
            return self._send(200, {"ok": True})
        except SystemExit as e:
            return self._send(400, {"error": str(e)})
        except Exception as e:  # report to the page instead of dropping the connection
            return self._send(500, {"error": "%s: %s" % (type(e).__name__, e)})

    def _install(self, q):
        name = os.path.basename(q.get("name", ["app.apk"])[0])
        ext = os.path.splitext(name)[1].lower()
        if ext not in EXTS:
            return self._send(400, {"error": "Not an Android app file (.apk, .xapk, .apkm)"})
        size = int(self.headers.get("Content-Length") or 0)
        if not 0 < size <= MAX_UPLOAD:
            return self._send(400, {"error": "empty or too large"})
        tmp = tempfile.mkdtemp(prefix="apkrunner-up-")
        try:
            path = os.path.join(tmp, "upload" + ext)
            with open(path, "wb") as f:
                left = size
                while left:
                    chunk = self.rfile.read(min(left, 1 << 20))
                    if not chunk:
                        break
                    f.write(chunk)
                    left -= len(chunk)
            before = set(macapp.registry())
            macapp.install(path)
            new = [p for p in macapp.registry() if p not in before]
            return self._send(200, {"ok": True, "package": new[0] if new else None})
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


def serve(port=0, open_browser=True):
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    url = "http://127.0.0.1:%d/?t=%s" % (srv.server_address[1], quote(TOKEN))
    print("Apk-Runner dashboard: %s" % url)
    print("Keep this window open while you use it. Press Ctrl+C to quit.")
    if open_browser:
        threading.Timer(0.3, webbrowser.open, (url,)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


PAGE = r"""<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Apk-Runner</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--fg:#1c1e21;--mute:#6b7280;--line:#e5e7eb;--acc:#2563eb;--acc-fg:#fff;--ok:#16a34a;--bad:#dc2626;--drop:#eef4ff}
@media (prefers-color-scheme:dark){:root{--bg:#111316;--card:#1b1e23;--fg:#e8eaed;--mute:#9aa0a6;--line:#2c3036;--acc:#5b8cff;--acc-fg:#0b0d10;--ok:#4ade80;--bad:#f87171;--drop:#18233a}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
header{display:flex;align-items:center;gap:12px;padding:18px 24px;border-bottom:1px solid var(--line);background:var(--card);position:sticky;top:0;z-index:2}
header h1{font-size:18px;margin:0;font-weight:650;letter-spacing:-.01em}
header .rt{margin-left:auto;font-size:13px;color:var(--mute)}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px;background:var(--ok);vertical-align:middle}
.dot.off{background:var(--bad)}
main{max-width:1100px;margin:0 auto;padding:24px 16px 60px}
.drop{border:2px dashed var(--line);border-radius:14px;padding:26px;text-align:center;color:var(--mute);background:var(--card);cursor:pointer;transition:.15s}
.drop.over{border-color:var(--acc);background:var(--drop);color:var(--fg)}
.drop b{color:var(--fg)}
.drop .btn{margin-top:10px}
.btn{border:0;border-radius:8px;padding:8px 14px;font:inherit;font-weight:600;cursor:pointer;background:var(--acc);color:var(--acc-fg)}
.btn.ghost{background:transparent;color:var(--mute);border:1px solid var(--line);font-weight:500;padding:6px 10px}
.btn.ghost:hover{color:var(--fg)}
.btn:disabled{opacity:.55;cursor:default}
h2{font-size:14px;text-transform:uppercase;letter-spacing:.06em;color:var(--mute);margin:30px 0 12px;font-weight:600}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:14px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 14px 14px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px;position:relative}
.tile img,.tile .ph{width:72px;height:72px;border-radius:18px;object-fit:cover;cursor:pointer}
.tile .ph{display:grid;place-items:center;background:var(--drop);color:var(--acc);font-size:30px;font-weight:700}
.tile .name{font-weight:600;word-break:break-word}
.tile .pkg{font-size:12px;color:var(--mute);word-break:break-all}
.tile .row{display:flex;gap:6px;margin-top:4px;flex-wrap:wrap;justify-content:center}
.badge{position:absolute;top:10px;right:10px;font-size:11px;color:var(--ok);font-weight:600}
.empty{color:var(--mute);padding:30px;text-align:center;border:1px solid var(--line);border-radius:14px;background:var(--card)}
#toast{position:fixed;left:50%;bottom:22px;transform:translateX(-50%);background:var(--fg);color:var(--bg);padding:10px 16px;border-radius:10px;font-size:14px;opacity:0;transition:.2s;pointer-events:none;max-width:90vw}
#toast.show{opacity:1}
select{font:inherit;font-size:13px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);padding:5px}
</style></head><body>
<header><h1>Apk-Runner</h1><span class="rt" id="rt"></span></header>
<main>
  <div class="drop" id="drop">
    <div><b>Drop .apk, .xapk or .apkm files here</b></div>
    <div>They're installed to your dashboard and open in their own window.</div>
    <button class="btn" id="pick">Choose files…</button>
    <input type="file" id="file" accept=".apk,.xapk,.apkm,.apks" multiple hidden>
  </div>
  <h2>Your apps</h2>
  <div id="apps" class="grid"></div>
</main>
<div id="toast"></div>
<script>
const T=new URLSearchParams(location.search).get('t');
const api=(p,o={})=>fetch(p,{...o,headers:{'X-Token':T,...(o.headers||{})}}).then(async r=>{const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||r.statusText);return j});
const $=s=>document.querySelector(s);
let tt;function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');clearTimeout(tt);tt=setTimeout(()=>t.classList.remove('show'),3500)}
function esc(s){return s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
async function load(){
  const d=await api('/api/apps');
  $('#rt').innerHTML=d.runtime.backend?`<span class="dot"></span>Runtime: ${esc(d.runtime.backend)} · v${esc(d.runtime.version)}`:`<span class="dot off"></span>No Android runtime found (run <code>apkrunner doctor</code>)`;
  const g=$('#apps');
  if(!d.apps.length){g.className='';g.innerHTML='<div class="empty">No apps yet. Drop an APK above to get started.</div>';return}
  g.className='grid';
  g.innerHTML=d.apps.map(a=>{const p=encodeURIComponent(a.package);return `<div class="tile">
    ${a.running?'<span class="badge">● running</span>':''}
    ${a.icon?`<img src="/api/icon?pkg=${p}&t=${encodeURIComponent(T)}" alt="" data-open="${esc(a.package)}">`:`<div class="ph" data-open="${esc(a.package)}">${esc(a.label[0]||'?')}</div>`}
    <div class="name">${esc(a.label)}</div><div class="pkg">${esc(a.package)}</div>
    <div class="row"><button class="btn" data-open="${esc(a.package)}" ${a.running?'disabled':''}>${a.running?'Running':'Open'}</button>
    <select data-rot="${esc(a.package)}" title="Orientation">${['auto','portrait','landscape'].map(o=>`<option ${o==a.orientation?'selected':''}>${o}</option>`).join('')}</select>
    <button class="btn ghost" data-rm="${esc(a.package)}" title="Remove">Remove</button></div></div>`}).join('');
}
document.addEventListener('click',async e=>{
  const o=e.target.closest('[data-open]'),r=e.target.closest('[data-rm]');
  try{
    if(o&&!o.disabled){await api('/api/open?pkg='+encodeURIComponent(o.dataset.open),{method:'POST'});toast('Opening… the app appears in its own window');setTimeout(load,800)}
    if(r&&confirm('Remove this app?')){await api('/api/remove?pkg='+encodeURIComponent(r.dataset.rm),{method:'POST'});toast('Removed');load()}
  }catch(err){toast(err.message)}
});
document.addEventListener('change',async e=>{const s=e.target.closest('[data-rot]');if(!s)return;
  try{await api(`/api/rotate?pkg=${encodeURIComponent(s.dataset.rot)}&side=${s.value}`,{method:'POST'});toast('Orientation: '+s.value)}catch(err){toast(err.message)}});
async function upload(files){
  for(const f of files){
    toast('Installing '+f.name+'…');
    try{await api('/api/install?name='+encodeURIComponent(f.name),{method:'POST',body:f});toast('Installed '+f.name)}
    catch(err){toast(f.name+': '+err.message)}
    await load();
  }
}
const dz=$('#drop');
$('#pick').onclick=e=>{e.stopPropagation();$('#file').click()};
dz.onclick=()=>$('#file').click();
$('#file').onchange=e=>{upload([...e.target.files]);e.target.value=''};
['dragenter','dragover'].forEach(n=>addEventListener(n,e=>{e.preventDefault();dz.classList.add('over')}));
['dragleave','drop'].forEach(n=>addEventListener(n,e=>{e.preventDefault();if(n=='drop'||e.target===dz)dz.classList.remove('over')}));
addEventListener('drop',e=>{const fs=[...e.dataTransfer.files];if(fs.length)upload(fs)});
load();setInterval(load,4000);
</script></body></html>"""
