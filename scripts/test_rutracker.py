#!/usr/bin/env python3
"""Тест RuTor — проверяем что видит российский сервер"""
import requests, urllib.parse, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"
UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

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
s.headers.update({"User-Agent": UA})

for mirror in ["https://rutor.info", "https://rutor.is"]:
    log(f"\n=== {mirror} ===")
    try:
        q = urllib.parse.quote("sting")
        r = s.get(f"{mirror}/search/0/0/0/{q}", timeout=12)
        log(f"status={r.status_code} len={len(r.text)}")
        
        soup = BeautifulSoup(r.text, "html.parser")
        
        # Проверяем — это заглушка или реальные результаты
        blocked = "Вечная блокировка" in r.text or "Новый Адрес" in r.text
        log(f"blocked={blocked}")
        
        tbl = soup.find("table", id="index")
        rows = tbl.select("tr")[1:] if tbl else []
        log(f"table#index: {'found' if tbl else 'NOT FOUND'}, rows={len(rows)}")
        
        if rows:
            for row in rows[:3]:
                cells = row.select("td")
                if len(cells) < 4: continue
                links = cells[1].select("a")
                title = links[-1].text.strip()[:60] if links else "?"
                magnet = cells[1].select_one("a[href^='magnet:']")
                seeds = cells[4].select_one("span.green, span") if len(cells) > 4 else None
                log(f"  [{seeds.text.strip() if seeds else '?'}s] {title} | magnet={'✅' if magnet else '❌'}")
        elif not blocked:
            # Показываем все таблицы для диагностики
            tables = soup.find_all("table")
            log(f"All tables: {[(t.get('id'), t.get('class')) for t in tables[:5]]}")
            log(f"HTML fragment (500-1200): {r.text[500:1200]}")
    except Exception as e:
        log(f"ERROR: {e}")
    push()
