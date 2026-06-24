#!/usr/bin/env python3
import requests
from bs4 import BeautifulSoup
import os

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")

results = []
def log(msg):
    print(msg)
    results.append(msg)

# Все известные зеркала RuTracker
MIRRORS = [
    "https://rutracker.org",
    "https://rutracker.net",
    "https://rutracker.nl",
    "https://rutracker.ru",
    "https://rutracker.cr",
    "https://rutracker.cc",
    "https://rutracker.nnm.club",
]

session = requests.Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
    "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
})

log("=== ТЕСТ ЗЕРКАЛ ===")
working_base = None
for mirror in MIRRORS:
    try:
        r = session.get(f"{mirror}/forum/index.php", timeout=10)
        log(f"{mirror} → {r.status_code} (len={len(r.text)})")
        if r.status_code == 200:
            working_base = mirror
            log(f"  ✅ РАБОТАЕТ: {mirror}")
            # Проверим наличие формы логина
            if "login" in r.text.lower() or "форум" in r.text.lower():
                log("  Форма/контент найдены")
            break
    except Exception as e:
        log(f"{mirror} → {type(e).__name__}: {str(e)[:60]}")

if not working_base:
    log("\nВСЕ ЗЕРКАЛА НЕДОСТУПНЫ")
    log("Причина: GitHub Actions IP заблокирован RuTracker (они блокируют датацентры)")
    log("Решение: нужен прокси или резидентский IP")
else:
    log(f"\nАвторизация на {working_base}...")
    r = session.post(f"{working_base}/forum/login.php", data={
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "Вход"
    }, timeout=15)
    log(f"Login POST: {r.status_code} -> {r.url}")
    if LOGIN.lower() in r.text.lower() or "logged" in r.text.lower():
        log("✅ Авторизация успешна!")
    else:
        log("❌ Авторизация не удалась")

with open("test-results.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(results))
