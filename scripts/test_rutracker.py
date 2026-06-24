#!/usr/bin/env python3
import requests, os, urllib.parse, traceback
from bs4 import BeautifulSoup

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
BASE = "https://rutracker.net/forum"
QUERY = "sting"

results = []
def log(msg): print(msg); results.append(str(msg))
def save():
    with open("test-results.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(results))

try:
    s = requests.Session()
    s.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
        "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
    })

    # 1. Логин
    log("=== 1. ЛОГИН ===")
    r = s.get(f"{BASE}/index.php", timeout=15)
    log(f"GET index: {r.status_code}")
    
    r2 = s.post(f"{BASE}/login.php", data={
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "Вход"
    }, timeout=15, headers={"Referer": f"{BASE}/index.php"})
    log(f"POST login: {r2.status_code} → {r2.url}")
    
    # Проверяем куки
    log(f"Куки после логина: {dict(s.cookies)}")
    logged = LOGIN.lower() in r2.text.lower() or "bb_session" in s.cookies
    log(f"Залогинен: {logged}")
    save()

    # 2. Поиск
    log(f"\n=== 2. ПОИСК: '{QUERY}' ===")
    encoded = urllib.parse.quote(QUERY)
    r3 = s.get(f"{BASE}/tracker.php?nm={encoded}", timeout=15,
               headers={"Referer": f"{BASE}/index.php"})
    log(f"Search: {r3.status_code} → {r3.url}")
    
    soup = BeautifulSoup(r3.text, "html.parser")
    
    # Проверяем не редирект ли на логин
    if "login" in r3.url.lower():
        log("❌ РЕДИРЕКТ НА ЛОГИН — сессия не сохранилась!")
        log(f"Куки: {dict(s.cookies)}")
        save(); exit()
    
    tbl = soup.find("table", id="tor-tbl")
    log(f"Таблица tor-tbl: {'найдена' if tbl else 'НЕ НАЙДЕНА'}")
    
    if tbl:
        rows = tbl.select("tbody tr.tCenter")
        log(f"Строк: {len(rows)}")
        for row in rows[:5]:
            topic_id = row.get("data-topic_id", "")
            title_el = row.select_one("a.tLink")
            size_el  = row.select_one("td.tor-size")
            seeds_el = row.select_one("b.seedmed")
            dl_el    = row.select_one("a.tr-dl")
            
            title  = title_el.text.strip()[:70] if title_el else "?"
            size   = size_el.text.strip() if size_el else "?"
            seeds  = seeds_el.text.strip() if seeds_el else "?"
            dl_url = f"{BASE}/{dl_el.get('href','')}" if dl_el else "нет"
            log(f"  [{seeds}s] {title} | {size} | dl={dl_url[:60]}")
    else:
        # Диагностика
        log("Все таблицы: " + str([t.get("id") for t in soup.find_all("table")][:8]))
        log("Фрагмент страницы: " + r3.text[500:1000])
    save()

    # 3. RuTor тест
    log(f"\n=== 3. RUTOR: '{QUERY}' ===")
    mirrors = ["https://rutor.info", "https://rutor.is"]
    for mirror in mirrors:
        try:
            r4 = s.get(f"{mirror}/search/0/0/0/{urllib.parse.quote(QUERY)}", timeout=10)
            log(f"{mirror}: {r4.status_code} (len={len(r4.text)})")
            if r4.status_code == 200:
                soup4 = BeautifulSoup(r4.text, "html.parser")
                rows4 = soup4.select("table#index tr")
                log(f"  Строк: {len(rows4)}")
                for row in rows4[1:4]:
                    cells = row.select("td")
                    if len(cells) >= 4:
                        title_a = cells[1].select_one("a:last-child")
                        magnet  = cells[1].select_one("a[href^='magnet:']")
                        seeds   = cells[4].select_one("span") if len(cells) > 4 else None
                        log(f"  [{seeds.text.strip() if seeds else '?'}s] "
                            f"{title_a.text.strip()[:60] if title_a else '?'} | "
                            f"magnet={'да' if magnet else 'нет'}")
                break
        except Exception as e:
            log(f"  {mirror}: {e}")
    save()

except Exception as e:
    log(f"FATAL: {e}")
    log(traceback.format_exc())
    save()

print("Done")
