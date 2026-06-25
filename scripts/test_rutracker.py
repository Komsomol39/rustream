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
s.headers.update({"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"})

# TPB — попробуем разные эндпоинты
log("=== TPB / apibay ===")
for url in [
    "https://apibay.org/q.php?q=sting&cat=0",
    "https://thepiratebay.org/api/v2/search/sting",
    "https://piratebay.live/apibay/q.php?q=sting",
]:
    try:
        r = s.get(url, timeout=8)
        log(f"  {url[-40:]}: {r.status_code} len={len(r.text)} content={r.text[:100]}")
    except Exception as e:
        log(f"  {url[-40:]}: ERROR {e}")
push()

# Kinozal — разберём HTML структуру
log("\n=== Kinozal HTML структура ===")
try:
    r = s.get("https://kinozal.tv/browse.php?s=sting&g=0&c=0&v=0&d=0&w=0&t=0&f=0", timeout=10)
    log(f"status={r.status_code}")
    soup = BeautifulSoup(r.text, "html.parser")
    
    # Все таблицы
    for t in soup.find_all("table")[:5]:
        log(f"  table id={t.get('id')} class={t.get('class')}")
    
    # Найти строки с результатами
    rows = soup.select("tr.bg")[:3]
    log(f"  tr.bg rows: {len(rows)}")
    if rows:
        log(f"  Первая строка: {str(rows[0])[:400]}")
    
    # Ищем любые ссылки на торренты
    links = soup.select("a[href*='details']") or soup.select("a[href*='torrent']")
    log(f"  detail links: {len(links)}")
    for a in links[:3]:
        log(f"    {a.text.strip()[:60]} → {a.get('href','')[:60]}")
except Exception as e:
    log(f"ERROR: {e}")
push()

# NNM-Club RSS (без авторизации)
log("\n=== NNM-Club RSS ===")
try:
    r = s.get("https://nnmclub.to/forum/rss.php?nm=sting", timeout=10)
    log(f"status={r.status_code} len={len(r.text)}")
    log(f"content: {r.text[:300]}")
except Exception as e:
    log(f"ERROR: {e}")
push()
