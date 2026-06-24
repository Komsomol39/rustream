#!/usr/bin/env python3
import requests
from bs4 import BeautifulSoup
import os, urllib.parse

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
BASE = "https://rutracker.net/forum"

results = []
def log(msg): print(msg); results.append(msg)

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
log(f"OK: logged_in={logged_in}")

# 2. Поиск + парсинг результатов
log("\n=== 2. ПОИСК И ПАРСИНГ ===")
query = "Interstellar"
r = session.get(f"{BASE}/tracker.php?nm={urllib.parse.quote(query)}", timeout=15)
soup = BeautifulSoup(r.text, "html.parser")
rows = soup.select("table#tor-tbl tbody tr.tCenter")
log(f"Результатов: {len(rows)}")

topic_id = None
for row in rows[:5]:
    title_el = row.select_one("td.t-title a.tLink")
    size_el  = row.select_one("td.tor-size")
    seeds_el = row.select_one("b.seedmed")
    cat_el   = row.select_one("td.f-name-col a")
    
    if not title_el: continue
    href = title_el.get("href","")
    if "t=" in href and not topic_id:
        topic_id = href.split("t=")[-1].split("&")[0]
    
    log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] "
        f"[{cat_el.text.strip()[:20] if cat_el else '?'}] "
        f"{title_el.text.strip()[:65]} | "
        f"{size_el.text.strip() if size_el else '?'}")

# 3. Страница топика — magnet и .torrent
log(f"\n=== 3. MAGNET (topic={topic_id}) ===")
r2 = session.get(f"{BASE}/viewtopic.php?t={topic_id}", timeout=15)
soup2 = BeautifulSoup(r2.text, "html.parser")

magnet = soup2.find("a", href=lambda h: h and h.startswith("magnet:"))
dl     = soup2.find("a", {"class": "dl-stub"}) or soup2.find("a", href=lambda h: h and "dl.php" in str(h))

if magnet:
    log(f"✅ MAGNET OK: {magnet['href'][:80]}...")
else:
    log("❌ Magnet не найден")

if dl:
    log(f"✅ TORRENT DL: {dl.get('href','')[:80]}")
else:
    log("❌ dl.php не найден")

# Итог
log("\n=== ИТОГ ===")
log(f"Авторизация:  {'✅ OK' if logged_in else '❌ FAIL'}")
log(f"Поиск:        {'✅ OK' if rows else '❌ FAIL'} ({len(rows)} результатов)")
log(f"Magnet:       {'✅ OK' if magnet else '❌ нет'}")
log(f"Torrent dl:   {'✅ OK' if dl else '❌ нет'}")
log("\nПарсер готов к интеграции в Android" if (logged_in and rows and (magnet or dl)) else "\nНужна доработка")

with open("test-results.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(results))
