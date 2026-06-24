#!/usr/bin/env python3
"""
Тест парсинга RuTracker.
Проверяет: логин, поиск, получение magnet-ссылки.
"""
import requests
from bs4 import BeautifulSoup
import os
import sys
import time

LOGIN = os.environ.get("RUTRACKER_LOGIN", "")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD", "")
BASE = "https://rutracker.org/forum"

results = []

def log(msg):
    print(msg)
    results.append(msg)

def test_login(session):
    log("\n=== ТЕСТ 1: Авторизация ===")
    # Получаем страницу логина
    r = session.get(f"{BASE}/index.php", timeout=15)
    log(f"Главная: {r.status_code}")
    if r.status_code != 200:
        log(f"FAIL: не удалось открыть главную ({r.status_code})")
        return False

    soup = BeautifulSoup(r.text, "html.parser")
    
    # Ищем форму логина
    login_form = soup.find("form", id="login-form")
    if not login_form:
        # Возможно уже залогинены или другая структура
        if "logged-in" in r.text or "profile" in r.text.lower():
            log("OK: уже авторизованы")
            return True
        log("FAIL: форма логина не найдена")
        log(f"Формы на странице: {[f.get('id') for f in soup.find_all('form')]}")
        return False

    # Отправляем логин
    login_data = {
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "Вход"
    }
    
    r2 = session.post(f"{BASE}/login.php", data=login_data, timeout=15,
        headers={"Referer": f"{BASE}/index.php"})
    log(f"POST login: {r2.status_code} -> {r2.url}")
    
    # Проверяем успех
    if "logged-in" in r2.text or LOGIN.lower() in r2.text.lower():
        log("OK: авторизация успешна")
        return True
    
    soup2 = BeautifulSoup(r2.text, "html.parser")
    error = soup2.find(class_="mrg_16")
    if error:
        log(f"FAIL: {error.text.strip()[:100]}")
    else:
        log("FAIL: авторизация не удалась (нет маркера успеха)")
        # Покажем кусок страницы для диагностики
        log(f"URL после логина: {r2.url}")
        log(f"Фрагмент: {r2.text[:300]}")
    return False

def test_search(session, query="Inception 2010"):
    log(f"\n=== ТЕСТ 2: Поиск '{query}' ===")
    import urllib.parse
    encoded = urllib.parse.quote(query)
    
    r = session.get(
        f"{BASE}/tracker.php?nm={encoded}",
        timeout=15,
        headers={"Referer": f"{BASE}/index.php"}
    )
    log(f"Search: {r.status_code}")
    if r.status_code != 200:
        log(f"FAIL: {r.status_code}")
        return []
    
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("table#tor-tbl tbody tr")
    log(f"Строк в таблице: {len(rows)}")
    
    items = []
    for row in rows[:5]:
        title_el = row.select_one("td.t-title a.tLink")
        size_el = row.select_one("td.tor-size")
        seeds_el = row.select_one("td.seedmed b")
        topic_id = None
        if title_el:
            href = title_el.get("href", "")
            # href = viewtopic.php?t=12345
            if "t=" in href:
                topic_id = href.split("t=")[-1].split("&")[0]
        
        if title_el:
            item = {
                "title": title_el.text.strip()[:80],
                "size": size_el.text.strip() if size_el else "?",
                "seeds": seeds_el.text.strip() if seeds_el else "0",
                "topic_id": topic_id,
            }
            items.append(item)
            log(f"  [{item['seeds']}s] {item['title']} | {item['size']}")
    
    if not items:
        log("FAIL: результатов нет")
        # Диагностика
        log(f"URL: {r.url}")
        tables = soup.find_all("table")
        log(f"Таблиц на странице: {len(tables)}")
        log(f"IDs таблиц: {[t.get('id') for t in tables[:5]]}")
    return items

def test_magnet(session, topic_id):
    log(f"\n=== ТЕСТ 3: Magnet для topic {topic_id} ===")
    r = session.get(
        f"{BASE}/viewtopic.php?t={topic_id}",
        timeout=15
    )
    log(f"Topic page: {r.status_code}")
    soup = BeautifulSoup(r.text, "html.parser")
    
    # Ищем magnet ссылку
    magnet = soup.find("a", href=lambda h: h and h.startswith("magnet:"))
    if magnet:
        href = magnet["href"]
        log(f"OK: magnet найден ({href[:60]}...)")
        return href
    
    # Ищем кнопку скачивания торрента
    dl = soup.find("a", class_="dl-stub")
    if dl:
        log(f"OK: ссылка на .torrent найдена: {dl.get('href','')[:60]}")
        return dl.get("href", "")
    
    log("FAIL: ни magnet ни torrent-ссылки не найдено")
    return None

# --- Запуск ---
session = requests.Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Accept-Language": "ru-RU,ru;q=0.9",
    "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
})

ok = test_login(session)
if ok:
    items = test_search(session, "Inception 2010")
    if items and items[0]["topic_id"]:
        test_magnet(session, items[0]["topic_id"])
    
    # Второй тест — русский запрос
    test_search(session, "Начало 2010")
else:
    log("\nПропускаем тесты поиска — нет авторизации")

log("\n=== ИТОГ ===")
log("LOGIN: " + ("OK" if ok else "FAIL"))

# Сохраняем результат
with open("test-results.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(results))
print("\nСохранено в test-results.txt")
