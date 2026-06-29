#!/usr/bin/env python3
import requests, json, base64, urllib.parse
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

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

# Kinozal
log("=== KINOZAL ===")
try:
    q = urllib.parse.quote("пацаны")
    r = s.get(f"https://kinozal.tv/browse.php?s={q}&g=0&c=0&v=0&d=0&w=0&t=0&f=0", timeout=12)
    log(f"status={r.status_code}")
    soup = BeautifulSoup(r.content.decode("windows-1251","replace"), "html.parser")
    
    # Текущий селектор
    rows1 = soup.select("table.t_peer tr.first.bg, table.t_peer tr.bg")
    log(f"table.t_peer tr.bg: {len(rows1)}")
    
    # Альтернативные селекторы
    rows2 = soup.select("tr.bg")
    log(f"tr.bg: {len(rows2)}")
    
    rows3 = soup.select("table.t_peer tr")
    log(f"table.t_peer tr: {len(rows3)}")
    
    # Ищем любые ссылки на details
    detail_links = soup.select("a[href*='details']")
    log(f"details links: {len(detail_links)}")
    for a in detail_links[:3]:
        log(f"  {a.text.strip()[:60]}")
    
    # Показываем структуру таблицы
    tbl = soup.find("table", class_="t_peer")
    if tbl:
        rows = tbl.find_all("tr")[:5]
        for row in rows:
            cls = row.get("class", [])
            tds = len(row.find_all("td"))
            log(f"  TR cls={cls} tds={tds}")
            if tds > 3:
                log(f"  HTML: {str(row)[:300]}")
                break
    else:
        # Все таблицы
        tables = soup.find_all("table")
        log(f"Tables: {[(t.get('class'), t.get('id')) for t in tables[:6]]}")
except Exception as e:
    import traceback
    log(f"ERROR: {e}"); log(traceback.format_exc())
push()
