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
import time
import urllib.request
import webbrowser
from urllib.parse import parse_qs, quote, urlparse

from . import __version__, backends, macapp, setup_android

TOKEN = secrets.token_urlsafe(16)
INSTANCE = os.path.join(macapp.STATE, "dashboard.json")
IDLE_EXIT = 180  # seconds without any page request before an app-launched dashboard quits
LAST_SEEN = [time.time()]
RUNNING = {}  # package -> Popen
LOCK = threading.Lock()
MAX_UPLOAD = 4 << 30
EXTS = (".apk", ".xapk", ".apkm", ".apks")


def _apps():
    out = []
    for pkg, e in sorted(macapp.registry().items(), key=lambda x: x[1]["label"].lower()):
        p = RUNNING.get(pkg)
        error = None
        if p and p.poll() not in (None, 0):
            error = _last_line(_log_path(pkg)) or "the app couldn't be opened"
        out.append({"package": pkg, "label": e["label"],
                    "orientation": e.get("override") or e.get("orientation") or "auto",
                    "icon": os.path.exists(os.path.join(macapp.ICONS, pkg + ".img")),
                    "running": bool(p and p.poll() is None) or pkg in WAITING,
                    "waiting": pkg in WAITING, "error": None if pkg in WAITING else error})
    return out


def _log_path(pkg):
    return os.path.join(macapp.STATE, "logs", pkg + ".log")


def _last_line(path):
    try:
        with open(path, errors="replace") as f:
            lines = [l.strip() for l in f.read().splitlines() if l.strip()]
    except OSError:
        return None
    # skip traceback noise: show the readable message
    lines = [l for l in lines if not l.startswith(("Traceback", "File ", "^"))]
    return lines[-1] if lines else None


def _runtime():
    b = backends.pick()
    prog = dict(setup_android.PROGRESS)
    return {"backend": b.name if b else None, "version": __version__,
            "needs_setup": b is None, "setup": prog}


WAITING = set()  # apps the user opened before Android was ready; opened when setup finishes


def _needs_setup():
    return backends.pick() is None


def _auto_setup_loop():
    """Set up Android without being asked, retry on failure, then open any waiting apps."""
    tries = 0
    while True:
        st = setup_android.PROGRESS.get("state")
        if _needs_setup() and st in ("idle", "error") and tries < 4:
            if st == "error":
                time.sleep(20 * tries)  # back off before retrying a failed download
            tries += 1
            setup_android.start_in_background()
        elif not _needs_setup():
            tries = 0
            for pkg in list(WAITING):
                WAITING.discard(pkg)
                _launch(pkg)
        time.sleep(2)


def _launch(pkg):
    if _needs_setup():
        WAITING.add(pkg)
        return
    with LOCK:
        p = RUNNING.get(pkg)
        if p and p.poll() is None:
            return
        os.makedirs(os.path.dirname(_log_path(pkg)), exist_ok=True)
        with open(_log_path(pkg), "w") as log:
            RUNNING[pkg] = subprocess.Popen(macapp.self_command() + ["--no-update-check", "launch", pkg],
                                            stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                            start_new_session=True)


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
        LAST_SEEN[0] = time.time()
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
            if url.path == "/api/setup":
                setup_android.start_in_background()
                return self._send(200, {"ok": True})
            if url.path == "/api/quit":
                self._send(200, {"ok": True})
                threading.Thread(target=self.server.shutdown, daemon=True).start()
                return
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


def _existing():
    """URL of a dashboard that is already running, if any."""
    try:
        with open(INSTANCE) as f:
            url = json.load(f)["url"]
        base, _, token = url.partition("/?t=")
        req = urllib.request.Request(base + "/api/apps", headers={"X-Token": token})
        urllib.request.urlopen(req, timeout=2).read()
        return url
    except Exception:
        return None


def _idle_watch(srv):
    while True:
        time.sleep(15)
        busy = WAITING or setup_android.PROGRESS.get("state") == "running"
        if not busy and time.time() - LAST_SEEN[0] > IDLE_EXIT and not any(
                p.poll() is None for p in RUNNING.values()):
            srv.shutdown()
            return


def serve(port=0, open_browser=True, idle_exit=None):
    """Run the dashboard. A second launch just reopens the browser on the first one."""
    url = _existing() if port == 0 else None
    if url:
        if open_browser:
            webbrowser.open(url)
        print("Apk-Runner dashboard is already running: %s" % url)
        return 0
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    url = "http://127.0.0.1:%d/?t=%s" % (srv.server_address[1], quote(TOKEN))
    macapp._save(INSTANCE, {"url": url, "pid": os.getpid()})
    print("Apk-Runner dashboard: %s" % url)
    print("Keep this window open while you use it. Press Ctrl+C to quit.")
    if idle_exit is None:  # launched from the .app: no Terminal to close, so quit when unused
        idle_exit = not sys.stdin or not sys.stdin.isatty()
    if idle_exit:
        threading.Thread(target=_idle_watch, args=(srv,), daemon=True).start()
    threading.Thread(target=_auto_setup_loop, daemon=True).start()
    if open_browser:
        threading.Timer(0.3, webbrowser.open, (url,)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
        try:
            os.remove(INSTANCE)
        except OSError:
            pass
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
.setup{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 20px;margin-bottom:16px;display:none}
.setup.show{display:block}
.setup h3{margin:0 0 4px;font-size:16px}
.setup p{margin:0 0 12px;color:var(--mute)}
.bar{height:8px;border-radius:4px;background:var(--line);overflow:hidden;margin:10px 0 6px}
.bar i{display:block;height:100%;background:var(--acc);width:0;transition:width .4s}
.err{color:var(--bad);font-size:12px;word-break:break-word}
.empty{color:var(--mute);padding:30px;text-align:center;border:1px solid var(--line);border-radius:14px;background:var(--card)}
#toast{position:fixed;left:50%;bottom:22px;transform:translateX(-50%);background:var(--fg);color:var(--bg);padding:10px 16px;border-radius:10px;font-size:14px;opacity:0;transition:.2s;pointer-events:none;max-width:90vw}
#toast.show{opacity:1}
select{font:inherit;font-size:13px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);padding:5px}
</style></head><body>
<header><h1>Apk-Runner</h1><span class="rt" id="rt"></span><button class="btn ghost" id="quit" title="Stop Apk-Runner">Quit</button></header>
<main>
  <div class="setup" id="setup"></div>
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
let d0=null;
async function load(){
  const d=await api('/api/apps');d0=d;
  const rt=d.runtime,st=rt.setup||{};
  $('#rt').innerHTML=rt.backend?`<span class="dot"></span>Runtime: ${esc(rt.backend)} · v${esc(rt.version)}`:`<span class="dot off"></span>Android not set up · v${esc(rt.version)}`;
  const box=$('#setup');
  if(st.state==='running'){
    const pct=st.total?Math.round(100*st.done/st.total):0;
    box.innerHTML=`<h3>Setting up Android…</h3><p>${esc(st.step||'')}${st.total?` — ${(st.done/1048576).toFixed(0)} of ${(st.total/1048576).toFixed(0)} MB`:''}</p><div class="bar"><i style="width:${pct}%"></i></div><p>Apps you open now will start by themselves when it's ready. This happens only once.</p>`;
    box.className='setup show';
  }else if(st.state==='error'){
    box.innerHTML=`<h3>Android setup didn't finish</h3><p class="err">${esc(st.error||'')}</p><p>Retrying automatically…</p><button class="btn ghost" data-setup>Retry now</button>`;box.className='setup show';
  }else if(st.state==='done'){
    box.innerHTML=`<h3>Android is ready</h3><p>Press Open on any app. The first start takes a few minutes while Android boots.</p>`;box.className='setup show';
  }else if(rt.needs_setup){
    box.innerHTML=`<h3>Preparing Android…</h3><p>Apk-Runner is downloading Google's Android runtime for this Mac (about 1.9 GB, one time only). Apps you open now will start by themselves when it's ready.</p><div class="bar"><i style="width:0"></i></div>`;box.className='setup show';
  }else box.className='setup';
  const g=$('#apps');
  if(!d.apps.length){g.className='';g.innerHTML='<div class="empty">No apps yet. Drop an APK above to get started.</div>';return}
  g.className='grid';
  g.innerHTML=d.apps.map(a=>{const p=encodeURIComponent(a.package);return `<div class="tile">
    ${a.running?'<span class="badge">● running</span>':''}
    ${a.icon?`<img src="/api/icon?pkg=${p}&t=${encodeURIComponent(T)}" alt="" data-open="${esc(a.package)}">`:`<div class="ph" data-open="${esc(a.package)}">${esc(a.label[0]||'?')}</div>`}
    <div class="name">${esc(a.label)}</div><div class="pkg">${esc(a.package)}</div>
    ${a.error?`<div class="err">${esc(a.error)}</div>`:''}
    <div class="row"><button class="btn" data-open="${esc(a.package)}" ${a.running?'disabled':''}>${a.waiting?'Waiting for Android…':a.running?'Running':'Open'}</button>
    <select data-rot="${esc(a.package)}" title="Orientation">${['auto','portrait','landscape'].map(o=>`<option ${o==a.orientation?'selected':''}>${o}</option>`).join('')}</select>
    <button class="btn ghost" data-rm="${esc(a.package)}" title="Remove">Remove</button></div></div>`}).join('');
}
document.addEventListener('click',async e=>{
  const o=e.target.closest('[data-open]'),r=e.target.closest('[data-rm]'),su=e.target.closest('[data-setup]');
  try{
    if(su){await api('/api/setup',{method:'POST'});toast('Downloading Android…');setTimeout(load,500);return}
    if(o&&!o.disabled){await api('/api/open?pkg='+encodeURIComponent(o.dataset.open),{method:'POST'});toast(d0&&d0.runtime&&d0.runtime.needs_setup?'It will open as soon as Android is ready':'Opening… the first start can take a few minutes');setTimeout(load,800)}
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
$('#quit').onclick=async()=>{if(!confirm('Quit Apk-Runner? Open apps keep running.'))return;
  try{await api('/api/quit',{method:'POST'})}catch(e){}
  clearInterval(timer);document.body.innerHTML='<main><div class="empty">Apk-Runner has quit. Open it again from Launchpad.</div></main>'};
load();const timer=setInterval(load,4000);
</script></body></html>"""
