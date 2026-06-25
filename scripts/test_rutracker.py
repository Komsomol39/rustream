#!/usr/bin/env python3
import requests, urllib.parse, json, base64, traceback
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"

results = []
def log(msg): print(msg); results.append(str(msg))
def push():
    content = "\n".join(results)
    with open("test-results.txt","w",encoding="utf-8") as f: f.write(content)
    api = f"https://api.github.com/repos/{REPO}/contents/test-results.txt"
    req = ur.Request(api, headers={"Authorization":f"token {GH_TOKEN}"})
    sha = None
    try:
        with ur.urlopen(req) as r: sha = json.loads(r.read())["sha"]
    except: pass
    body = {"message":"test results [skip ci]","content":base64.b64encode(content.encode()).decode(),"branch":"main"}
    if sha: body["sha"] = sha
    req2 = ur.Request(api, data=json.dumps(body).encode(), method="PUT",
        headers={"Authorization":f"token {GH_TOKEN}","Content-Type":"application/json"})
    try:
        with ur.urlopen(req2): pass
    except: pass

s = requests.Session()
s.headers.update({"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})

# 1. TPB API (apibay.org) — JSON, работает глобально
log("=== TPB API (apibay.org) ===")
try:
    r = s.get("https://apibay.org/q.php?q=sting&cat=0", timeout=10)
    data = r.json()
    log(f"status={r.status_code} results={len(data)}")
    for item in data[:3]:
        log(f"  [{item.get('seeders','?')}s] {item.get('name','?')[:60]} | {int(item.get('size',0))//1024//1024}MB")
        log(f"    magnet=magnet:?xt=urn:btih:{item.get('info_hash','')}&dn={urllib.parse.quote(item.get('name',''))}")
except Exception as e:
    log(f"ERROR: {e}")
push()

# 2. Kinozal — российский трекер, без авторизации
log("\n=== Kinozal (без авторизации) ===")
try:
    r = s.get("https://kinozal.tv/browse.php?s=sting&g=0&c=0&v=0&d=0&w=0&t=0&f=0", timeout=10)
    log(f"status={r.status_code}")
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("table.t_peer tr.bg")[: 3]
    log(f"rows={len(rows)}")
    for row in rows:
        title = row.select_one("a.nam")
        log(f"  {title.text.strip()[:60] if title else '?'}")
except Exception as e:
    log(f"ERROR: {e}")
push()

# 3. 1337x  
log("\n=== 1337x ===")
try:
    r = s.get("https://1337x.to/search/sting/1/", timeout=10)
    log(f"status={r.status_code}")
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("table.table-list tbody tr")[:3]
    log(f"rows={len(rows)}")
    for row in rows:
        title = row.select_one("td.name a:last-child")
        seeds = row.select_one("td.seeds")
        log(f"  [{seeds.text.strip() if seeds else '?'}s] {title.text.strip()[:60] if title else '?'}")
except Exception as e:
    log(f"ERROR: {e}")
push()

# 4. Rutor — проверим реальный ответ с российского IP через другой подход
log("\n=== RuTor прямой IP доступ ===")
try:
    # Попробуем обойти блокировку через direct IP
    r = s.get("https://rutor.info/search/0/0/0/sting",
        timeout=10,
        headers={"X-Forwarded-For": "77.88.8.8"})  # Яндекс DNS IP
    soup = BeautifulSoup(r.text, "html.parser")
    tbl = soup.find("table", id="index")
    rows = tbl.select("tr")[1:] if tbl else []
    log(f"status={r.status_code} table={'✅' if tbl else '❌'} rows={len(rows)}")
except Exception as e:
    log(f"ERROR: {e}")
push()
