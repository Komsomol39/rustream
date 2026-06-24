#!/usr/bin/env python3
import requests, os, urllib.parse, traceback
from bs4 import BeautifulSoup

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
QUERY = "sting"
MIRRORS = [
    "https://rutracker.net",
    "https://rutracker.org",
    "https://rutracker.nl",
    "https://rutracker.cc",
]

results = []
def log(msg): print(msg); results.append(str(msg))
def save():
    with open("test-results.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(results))

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

try:
    # Найти рабочее зеркало + залогиниться
    log("=== 1. ПОИСК РАБОЧЕГО ЗЕРКАЛА + ЛОГИН ===")
    working_base = None
    session = None

    for mirror in MIRRORS:
        s = requests.Session()
        s.headers.update(HEADERS)
        try:
            log(f"Пробуем {mirror} ...")
            r = s.post(f"{mirror}/forum/login.php", data={
                "login_username": LOGIN,
                "login_password": PASSWORD,
                "login": "Вход"
            }, timeout=20, headers={"Referer": f"{mirror}/forum/index.php"})
            log(f"  {r.status_code} → {r.url}")
            if r.status_code == 200 and LOGIN.lower() in r.text.lower():
                log(f"  ✅ ЗАЛОГИНЕН на {mirror}")
                working_base = f"{mirror}/forum"
                session = s
                break
            else:
                log(f"  ❌ Не залогинен (status={r.status_code})")
        except Exception as e:
            log(f"  ❌ {type(e).__name__}: {str(e)[:80]}")
        save()

    if not working_base:
        log("\n❌ Ни одно зеркало не работает")
        save(); exit()

    # Поиск
    log(f"\n=== 2. ПОИСК '{QUERY}' на {working_base} ===")
    r2 = session.get(
        f"{working_base}/tracker.php?nm={urllib.parse.quote(QUERY)}",
        timeout=20,
        headers={"Referer": f"{working_base}/index.php"}
    )
    log(f"HTTP: {r2.status_code} → {r2.url}")
    save()

    soup = BeautifulSoup(r2.text, "html.parser")
    tbl = soup.find("table", id="tor-tbl")
    if not tbl:
        log("❌ Таблица tor-tbl не найдена")
        log(f"Таблицы: {[t.get('id') for t in soup.find_all('table')][:5]}")
        log(f"Фрагмент: {r2.text[1000:2000]}")
        save(); exit()

    rows = tbl.select("tbody tr.tCenter")
    log(f"✅ Результатов: {len(rows)}")
    for row in rows[:5]:
        topic_id = row.get("data-topic_id", "?")
        title_el = row.select_one("a.tLink")
        size_el  = row.select_one("td.tor-size")
        seeds_el = row.select_one("b.seedmed")
        dl_el    = row.select_one("a.tr-dl")
        log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] "
            f"{title_el.text.strip()[:65] if title_el else '?'} | "
            f"{size_el.text.strip() if size_el else '?'} | "
            f"id={topic_id}")
    save()

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
                        title_a = cells[1].select("a")
                        title = title_a[-1].text.strip()[:60] if title_a else "?"
                        magnet = cells[1].select_one("a[href^='magnet:']")
                        seeds = cells[4].select_one("span")
                        log(f"  [{seeds.text.strip() if seeds else '?'}s] {title} | magnet={'✅' if magnet else '❌'}")
                break
        except Exception as e:
            log(f"  {mirror}: {e}")
    save()

    log("\n=== ИТОГ ===")
    log(f"RuTracker: ✅ {working_base} — {len(rows)} результатов")

except Exception as e:
    log(f"FATAL: {e}")
    log(traceback.format_exc())
    save()
print("Done")
