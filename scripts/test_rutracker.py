#!/usr/bin/env python3
"""Диагностика HTML структуры страницы результатов RuTracker"""
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
    })

    # Логин
    session.post(f"{BASE}/login.php", data={
        "login_username": LOGIN, "login_password": PASSWORD, "login": "Вход"
    }, timeout=15)

    # Поиск
    r = session.get(f"{BASE}/tracker.php?nm=Interstellar", timeout=15)
    soup = BeautifulSoup(r.text, "html.parser")

    # Диагностика таблицы
    tbl = soup.find("table", id="tor-tbl")
    if not tbl:
        log("Таблица tor-tbl НЕ НАЙДЕНА")
        log(f"Все таблицы: {[t.get('id') for t in soup.find_all('table')]}")
        save(); exit()

    log(f"✅ Таблица tor-tbl найдена")
    tbody = tbl.find("tbody")
    rows = tbody.find_all("tr") if tbody else []
    log(f"Строк в tbody: {len(rows)}")

    if rows:
        row = rows[0]
        log(f"\n--- Первая строка HTML (первые 800 символов) ---")
        log(str(row)[:800])
        log("---")

        # Попробуем разные селекторы для заголовка
        log("\n--- Поиск заголовка ---")
        for sel in ["td.t-title a.tLink", "a.tLink", "td.t-title a", ".t-title a", "a[href*=viewtopic]"]:
            el = row.select_one(sel)
            if el:
                log(f"  ✅ selector='{sel}' → '{el.text.strip()[:60]}' href={el.get('href','')[:50]}")
            else:
                log(f"  ❌ selector='{sel}'")

        # Все ссылки в строке
        links = row.find_all("a", href=True)
        log(f"\nВсе ссылки в строке ({len(links)}):")
        for a in links[:6]:
            log(f"  <a class='{a.get('class')}' href='{a.get('href','')[:60]}'>{a.text.strip()[:40]}</a>")

        # Все td
        tds = row.find_all("td")
        log(f"\nTD классы: {[td.get('class') for td in tds]}")

    # Тест magnet на конкретной известной теме (для диагностики)
    log("\n=== ТЕСТ MAGNET (известная тема) ===")
    # Берём первую найденную ссылку viewtopic
    vt_links = soup.find_all("a", href=lambda h: h and "viewtopic.php" in str(h))
    if vt_links:
        topic_url = BASE + "/" + vt_links[0]["href"].lstrip("./")
        log(f"Открываем: {topic_url}")
        r2 = session.get(topic_url, timeout=15)
        soup2 = BeautifulSoup(r2.text, "html.parser")
        
        magnet = soup2.find("a", href=lambda h: h and h.startswith("magnet:"))
        dl     = soup2.find("a", href=lambda h: h and "dl.php" in str(h))
        log(f"Magnet: {'✅ ' + magnet['href'][:70] if magnet else '❌ нет'}")
        log(f"DL:     {'✅ ' + dl['href'][:70] if dl else '❌ нет'}")
    else:
        log("viewtopic ссылки не найдены в результатах поиска")

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc())

save()
print("Готово")
