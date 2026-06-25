#!/usr/bin/env python3
import requests, os, urllib.parse, traceback, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

LOGIN    = os.environ.get("RUTRACKER_LOGIN","")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD","")
GH_TOKEN = os.environ.get("GITHUB_TOKEN","")
REPO     = "Komsomol39/rustream"
BASE     = "https://rutracker.net/forum"

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
    except Exception as e: print(f"push err: {e}")

try:
    s = requests.Session()
    s.headers.update({
        "User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
        "Accept-Language":"ru-RU,ru;q=0.9",
        "Accept":"text/html,application/xhtml+xml,*/*;q=0.8",
    })

    # Логин
    log("=== ЛОГИН ===")
    s.get(f"{BASE}/index.php", timeout=20)
    r = s.post(f"{BASE}/login.php", data={
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "вход",
    }, timeout=25, headers={"Referer": f"{BASE}/index.php"})
    log(f"POST: {r.status_code} cookies={list(s.cookies.keys())}")
    
    # Проверка сессии
    bb_session = s.cookies.get("bb_session","")
    logged = bool(bb_session) and not bb_session.startswith("0-0-")
    log(f"bb_session={bb_session[:30]}... logged={logged}")
    push()

    # Тест трёх запросов
    for query in ["пилот", "sting", "Interstellar"]:
        log(f"\n=== ПОИСК: '{query}' ===")
        enc = urllib.parse.quote(query)
        r2 = s.get(f"{BASE}/tracker.php?nm={enc}", timeout=25,
            headers={"Referer": f"{BASE}/index.php"})
        log(f"HTTP {r2.status_code}")
        soup = BeautifulSoup(r2.text, "html.parser")
        rows = soup.select("table#tor-tbl tbody tr.tCenter")
        log(f"Результатов: {len(rows)}")
        for row in rows[:3]:
            title_el = row.select_one("a.tLink")
            seeds_el = row.select_one("b.seedmed")
            size_el  = row.select_one("td.tor-size")
            log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] "
                f"{title_el.text.strip()[:65] if title_el else '?'} | "
                f"{size_el.text.strip() if size_el else '?'}")
        push()

    log("\n=== ИТОГ ===")
    log(f"Авторизация: {'✅' if logged else '❌'}")

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc())
push()
