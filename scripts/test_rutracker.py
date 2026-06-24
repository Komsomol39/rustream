#!/usr/bin/env python3
import requests, os, urllib.parse, traceback, json, base64
from bs4 import BeautifulSoup

LOGIN    = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
GH_TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO     = "Komsomol39/rustream"
QUERY    = "sting"
MIRRORS  = ["https://rutracker.net", "https://rutracker.org", "https://rutracker.nl"]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

results = []
def log(msg): print(msg); results.append(str(msg))

def push_results():
    """Сохраняем через GitHub Contents API — без git"""
    content = "\n".join(results)
    with open("test-results.txt", "w", encoding="utf-8") as f:
        f.write(content)
    # Пушим через API
    api_url = f"https://api.github.com/repos/{REPO}/contents/test-results.txt"
    req = urllib.request.Request(api_url, headers={"Authorization": f"token {GH_TOKEN}"})
    sha = None
    try:
        import urllib.request as ur
        with ur.urlopen(req) as r:
            sha = json.loads(r.read())["sha"]
    except: pass
    import urllib.request as ur
    body = {
        "message": "test results [skip ci]",
        "content": base64.b64encode(content.encode()).decode(),
        "branch": "main"
    }
    if sha: body["sha"] = sha
    req2 = ur.Request(api_url, data=json.dumps(body).encode(), method="PUT",
        headers={"Authorization": f"token {GH_TOKEN}", "Content-Type": "application/json"})
    try:
        with ur.urlopen(req2) as r:
            print("✓ Результаты сохранены в репо")
    except Exception as e:
        print(f"Не удалось сохранить: {e}")

import urllib.request

try:
    log("=== 1. ПОИСК ЗЕРКАЛА + ЛОГИН ===")
    working_base = None
    session = None

    for mirror in MIRRORS:
        s = requests.Session()
        s.headers.update(HEADERS)
        try:
            log(f"Пробуем {mirror}...")
            r = s.post(f"{mirror}/forum/login.php", data={
                "login_username": LOGIN,
                "login_password": PASSWORD,
                "login": "Вход"
            }, timeout=25)
            logged = LOGIN.lower() in r.text.lower()
            log(f"  {r.status_code} | logged={logged} | cookies={list(s.cookies.keys())}")
            if r.status_code == 200 and logged:
                working_base = f"{mirror}/forum"
                session = s
                log(f"  ✅ {mirror} работает!")
                break
            else:
                log(f"  ❌ не залогинен")
        except Exception as e:
            log(f"  ❌ {type(e).__name__}: {str(e)[:60]}")
        push_results()

    if not working_base:
        log("❌ Ни одно зеркало не доступно")
        push_results()
        exit()

    # Поиск
    log(f"\n=== 2. ПОИСК '{QUERY}' ===")
    r2 = session.get(
        f"{working_base}/tracker.php?nm={urllib.parse.quote(QUERY)}",
        timeout=25
    )
    log(f"HTTP {r2.status_code} → {r2.url}")
    push_results()

    soup = BeautifulSoup(r2.text, "html.parser")
    tbl = soup.find("table", id="tor-tbl")
    if not tbl:
        log("❌ Таблица не найдена")
        push_results()
        exit()

    rows = tbl.select("tbody tr.tCenter")
    log(f"✅ Результатов: {len(rows)}")
    for row in rows[:5]:
        title_el = row.select_one("a.tLink")
        size_el  = row.select_one("td.tor-size")
        seeds_el = row.select_one("b.seedmed")
        dl_el    = row.select_one("a.tr-dl")
        log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] "
            f"{title_el.text.strip()[:65] if title_el else '?'} | "
            f"{size_el.text.strip() if size_el else '?'}")
    push_results()

    # RuTor
    log(f"\n=== 3. RUTOR '{QUERY}' ===")
    rs = requests.Session()
    rs.headers.update(HEADERS)
    for mirror in ["https://rutor.info", "https://rutor.is"]:
        try:
            r3 = rs.get(f"{mirror}/search/0/0/0/{urllib.parse.quote(QUERY)}", timeout=12)
            log(f"{mirror}: {r3.status_code}")
            if r3.status_code == 200:
                soup3 = BeautifulSoup(r3.text, "html.parser")
                rows3 = soup3.select("table#index tr")[1:6]
                log(f"  Результатов: {len(rows3)}")
                for row in rows3:
                    cells = row.select("td")
                    if len(cells) >= 5:
                        links = cells[1].select("a")
                        title = links[-1].text.strip()[:60] if links else "?"
                        magnet = cells[1].select_one("a[href^=\'magnet:\']")
                        seeds = cells[4].select_one("span")
                        log(f"  [{seeds.text.strip() if seeds else '?'}s] {title} | magnet={'✅' if magnet else '❌'}")
                break
        except Exception as e:
            log(f"  {e}")

    log("\n=== ИТОГ ===")
    log(f"RuTracker ({working_base}): ✅ {len(rows)} результатов")
    push_results()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc())
    push_results()
