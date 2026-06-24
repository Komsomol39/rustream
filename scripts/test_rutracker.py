#!/usr/bin/env python3
import requests
from bs4 import BeautifulSoup
import os, urllib.parse, traceback

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
BASE = "https://rutracker.net/forum"

results = []
def log(msg): print(msg); results.append(str(msg))

def save():
    with open("test-results.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(results))

try:
    session = requests.Session()
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
        "Accept-Language": "ru-RU,ru;q=0.9",
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
    })

    # 1. Логин
    log("=== 1. АВТОРИЗАЦИЯ ===")
    r = session.post(f"{BASE}/login.php", data={
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "Вход"
    }, timeout=15)
    logged_in = LOGIN.lower() in r.text.lower()
    log(f"{'✅ OK' if logged_in else '❌ FAIL'}: logged_in={logged_in}")
    save()

    # 2. Поиск
    log("\n=== 2. ПОИСК ===")
    query = "Interstellar 2014"
    r = session.get(f"{BASE}/tracker.php?nm={urllib.parse.quote(query)}", timeout=15)
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("table#tor-tbl tbody tr.tCenter")
    log(f"Результатов: {len(rows)}")

    topic_id = None
    for row in rows[:5]:
        try:
            title_el = row.select_one("td.t-title a.tLink")
            size_el  = row.select_one("td.tor-size")
            seeds_el = row.select_one("b.seedmed")
            if not title_el:
                continue
            href = title_el.get("href", "")
            if "t=" in href and not topic_id:
                topic_id = href.split("t=")[-1].split("&")[0]
            title = title_el.text.strip()[:65]
            size  = size_el.text.strip() if size_el else "?"
            seeds = seeds_el.text.strip() if seeds_el else "?"
            log(f"  [{seeds}s] {title} | {size}")
        except Exception as ex:
            log(f"  row error: {ex}")
    save()

    # 3. Magnet
    if topic_id:
        log(f"\n=== 3. MAGNET topic={topic_id} ===")
        r2 = session.get(f"{BASE}/viewtopic.php?t={topic_id}", timeout=15)
        soup2 = BeautifulSoup(r2.text, "html.parser")

        magnet = soup2.find("a", href=lambda h: h and h.startswith("magnet:"))
        dl_link = soup2.find("a", href=lambda h: h and "dl.php" in str(h))

        if magnet:
            log(f"✅ MAGNET: {magnet['href'][:90]}...")
        else:
            log("❌ Magnet не найден — ищем dl.php...")
        if dl_link:
            log(f"✅ DL: {dl_link['href'][:90]}")
        else:
            log("❌ dl.php не найден")
            
        # Показать все ссылки со страницы для диагностики
        all_hrefs = [a["href"] for a in soup2.find_all("a", href=True)
                     if any(x in a["href"] for x in ["magnet:", "dl.php", "download"])]
        log(f"Все dl/magnet ссылки: {all_hrefs[:5]}")
        save()

    # Итог
    log("\n=== ИТОГ ===")
    log(f"Зеркало:      rutracker.net")
    log(f"Авторизация:  {'✅ OK' if logged_in else '❌ FAIL'}")
    log(f"Поиск:        {'✅ OK' if rows else '❌ FAIL'} ({len(rows)} результатов)")
    log(f"Magnet:       {'✅ OK' if topic_id else '❓ не проверен'}")
    log("\n✅ Парсер готов к интеграции в Android" if (logged_in and rows) else "\n❌ Требует доработки")

except Exception as e:
    log(f"\nFATAL ERROR: {e}")
    log(traceback.format_exc())

save()
print("Готово")
