#!/usr/bin/env python3
import requests, os, urllib.parse, traceback, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

LOGIN    = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
GH_TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO     = "Komsomol39/rustream"
QUERY    = "sting"
BASE     = "https://rutracker.net/forum"

results = []
def log(msg): print(msg); results.append(str(msg))

def push():
    content = "\n".join(results)
    with open("test-results.txt","w",encoding="utf-8") as f: f.write(content)
    api = f"https://api.github.com/repos/{REPO}/contents/test-results.txt"
    req = ur.Request(api, headers={"Authorization": f"token {GH_TOKEN}"})
    sha = None
    try:
        with ur.urlopen(req) as r: sha = json.loads(r.read())["sha"]
    except: pass
    body = {"message":"test results [skip ci]","content":base64.b64encode(content.encode()).decode(),"branch":"main"}
    if sha: body["sha"] = sha
    req2 = ur.Request(api, data=json.dumps(body).encode(), method="PUT",
        headers={"Authorization":f"token {GH_TOKEN}","Content-Type":"application/json"})
    try:
        with ur.urlopen(req2): print("✓ pushed")
    except Exception as e: print(f"push failed: {e}")

try:
    s = requests.Session()
    s.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
        "Accept-Language": "ru-RU,ru;q=0.9",
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
    })

    # Логин
    log("=== ЛОГИН ===")
    r = s.post(f"{BASE}/login.php", data={
        "login_username": LOGIN, "login_password": PASSWORD, "login": "Вход"
    }, timeout=25, headers={"Referer": f"{BASE}/index.php"})
    log(f"  {r.status_code} logged={LOGIN.lower() in r.text.lower()} cookies={list(s.cookies.keys())}")
    push()

    # Пробуем разные варианты поиска
    log("\n=== ПОИСК — варианты URL ===")
    search_urls = [
        f"{BASE}/tracker.php?nm={urllib.parse.quote(QUERY)}",
        f"{BASE}/tracker.php?nm={urllib.parse.quote(QUERY)}&o=1&s=0&tm=-1",
        f"{BASE}/tracker.php?search_id=all&nm={urllib.parse.quote(QUERY)}",
    ]
    for search_url in search_urls:
        try:
            r2 = s.get(search_url, timeout=25, headers={
                "Referer": f"{BASE}/index.php",
                "X-Requested-With": "",
            })
            soup = BeautifulSoup(r2.text, "html.parser")
            tbl = soup.find("table", id="tor-tbl")
            rows = tbl.select("tbody tr.tCenter") if tbl else []
            log(f"  {r2.status_code} | tbl={'✅' if tbl else '❌'} | rows={len(rows)} | {search_url[-50:]}")
            if rows:
                for row in rows[:3]:
                    title_el = row.select_one("a.tLink")
                    seeds_el = row.select_one("b.seedmed")
                    size_el  = row.select_one("td.tor-size")
                    log(f"    [{seeds_el.text.strip() if seeds_el else '?'}s] {title_el.text.strip()[:65] if title_el else '?'} | {size_el.text.strip() if size_el else '?'}")
                break
            elif r2.status_code != 200:
                log(f"    Фрагмент: {r2.text[:200]}")
        except Exception as e:
            log(f"  ERROR: {e}")
        push()

    log("\n=== RUTOR ===")
    rs = requests.Session()
    rs.headers.update({"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    for mirror in ["https://rutor.info", "https://rutor.is"]:
        try:
            r3 = rs.get(f"{mirror}/search/0/0/0/{urllib.parse.quote(QUERY)}", timeout=12)
            log(f"{mirror}: {r3.status_code}")
            if r3.status_code == 200:
                soup3 = BeautifulSoup(r3.text, "html.parser")
                rows3 = soup3.select("table#index tr")[1:4]
                for row in rows3:
                    cells = row.select("td")
                    if len(cells) >= 4:
                        links = cells[1].select("a")
                        title = links[-1].text.strip()[:60] if links else "?"
                        magnet = "✅" if cells[1].select_one("a[href^=\'magnet:\']") else "❌"
                        seeds = cells[4].select_one("span") if len(cells)>4 else None
                        log(f"  [{seeds.text.strip() if seeds else '?'}s] {title} magnet={magnet}")
                break
        except Exception as e:
            log(f"  {e}")
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
