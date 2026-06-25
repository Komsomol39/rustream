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

try:
    s = requests.Session()
    s.headers.update({"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"})

    # Тест rutor.is — реальный сайт
    mirrors = ["https://rutor.is", "https://rutorka.org", "https://2rutor.org"]
    
    for mirror in mirrors:
        log(f"\n=== {mirror} ===")
        try:
            r = s.get(f"{mirror}/search/0/0/0/{urllib.parse.quote('sting')}", timeout=12)
            log(f"  status={r.status_code} len={len(r.text)}")
            soup = BeautifulSoup(r.text, "html.parser")
            
            tables = soup.find_all("table")
            log(f"  Таблиц: {len(tables)}, ids: {[t.get('id') for t in tables]}")
            
            # Ищем таблицу с результатами
            tbl = soup.find("table", id="index")
            if tbl:
                rows = tbl.find_all("tr")[1:]
                log(f"  ✅ table#index rows={len(rows)}")
                if rows:
                    # Показываем HTML первой строки
                    log(f"  Первая строка: {str(rows[0])[:500]}")
            else:
                # Ищем ссылки на торренты
                links = soup.select("a[href*='/torrent/']")
                real = [a for a in links if a.text.strip() and len(a.text.strip()) > 10]
                log(f"  Ссылок /torrent/: {len(real)}")
                for a in real[:3]:
                    log(f"    {a.text.strip()[:60]}")
        except Exception as e:
            log(f"  ERROR: {e}")
        push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
