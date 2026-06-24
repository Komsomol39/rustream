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

# Логин
log("=== 1. АВТОРИЗАЦИЯ ===")
r = session.post(f"{BASE}/login.php", data={
    "login_username": LOGIN,
    "login_password": PASSWORD,
    "login": "Вход"
}, timeout=15)
logged_in = LOGIN.lower() in r.text.lower() or "logged-in" in r.text
log(f"Status: {r.status_code} | logged_in={logged_in}")

# Поиск
log("\n=== 2. ПОИСК ===")
for query in ["Inception 2010", "Интерстеллар"]:
    log(f"Запрос: {query}")
    encoded = urllib.parse.quote(query)
    r = session.get(f"{BASE}/tracker.php?nm={encoded}", timeout=15,
        headers={"Referer": f"{BASE}/index.php"})
    log(f"  HTTP: {r.status_code} | url: {r.url}")
    
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("table#tor-tbl tbody tr.tCenter")
    log(f"  Строк найдено: {len(rows)}")
    
    topic_id = None
    for row in rows[:3]:
        title_el = row.select_one("td.t-title a.tLink")
        size_el = row.select_one("td.tor-size")
        seeds_el = row.select_one("b.seedmed") or row.select_one("td.seedmed b")
        
        if title_el:
            href = title_el.get("href","")
            if "t=" in href:
                topic_id = href.split("t=")[-1].split("&")[0]
            log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] {title_el.text.strip()[:70]} | {size_el.text.strip() if size_el else '?'}")
    
    if not rows:
        # Диагностика
        tables = soup.find_all("table")
        log(f"  Таблиц на странице: {len(tables)}, ids: {[t.get('id') for t in tables[:5]]}")
        # Проверим авторизацию
        if "login" in r.url or "login" in r.text[:500].lower():
            log("  ПРОБЛЕМА: редирект на логин — сессия не сохранилась")
    
    if topic_id:
        # Тест 3: magnet
        log(f"\n=== 3. MAGNET для topic={topic_id} ===")
        r2 = session.get(f"{BASE}/viewtopic.php?t={topic_id}", timeout=15)
        soup2 = BeautifulSoup(r2.text, "html.parser")
        
        magnet = soup2.find("a", href=lambda h: h and h.startswith("magnet:"))
        if magnet:
            href = magnet["href"]
            log(f"✅ MAGNET: {href[:80]}...")
        else:
            dl = soup2.find("a", class_=lambda c: c and "dl" in c.lower())
            log(f"Magnet не найден, dl-ссылка: {dl}")
            # Ищем любую ссылку на скачивание
            all_a = soup2.find_all("a", href=True)
            dl_links = [a["href"] for a in all_a if "dl.php" in a.get("href","") or "magnet" in a.get("href","")]
            log(f"dl/magnet ссылки: {dl_links[:3]}")
        break

with open("test-results.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(results))
print("Готово")
