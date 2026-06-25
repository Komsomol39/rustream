#!/usr/bin/env python3
"""Диагностика NNM-Club: логин + структура страницы поиска"""
import requests, urllib.parse, json, base64, traceback
import urllib.request as ur
from bs4 import BeautifulSoup

NNM_LOGIN    = __import__("os").environ.get("NNM_LOGIN","")
NNM_PASSWORD = __import__("os").environ.get("NNM_PASSWORD","")
GH_TOKEN     = __import__("os").environ.get("GITHUB_TOKEN","")
REPO         = "Komsomol39/rustream"
BASE         = "https://nnmclub.to/forum"
UA           = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

results = []
def log(msg): print(msg); results.append(str(msg))
def push():
    content = "\n".join(results)
    with open("test-results.txt","w",encoding="utf-8") as f: f.write(content)
    api = f"https://api.github.com/repos/{REPO}/contents/test-results.txt"
    req = ur.Request(api, headers={"Authorization":f"token {GH_TOKEN}"})
    sha = None
    try:
        with ur.urlopen(req) as r: sha = json.loads(r.read())["sha"]
    except: pass
    body = {"message":"test results [skip ci]","content":base64.b64encode(content.encode()).decode(),"branch":"main"}
    if sha: body["sha"] = sha
    req2 = ur.Request(api, data=json.dumps(body).encode(), method="PUT",
        headers={"Authorization":f"token {GH_TOKEN}","Content-Type":"application/json"})
    try:
        with ur.urlopen(req2): pass
    except Exception as e: print(f"push err: {e}")

try:
    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})

    # 1. GET страницы логина — смотрим форму
    log("=== 1. ФОРМА ЛОГИНА ===")
    r0 = s.get(f"{BASE}/login.php", timeout=15)
    log(f"GET: {r0.status_code}")
    soup0 = BeautifulSoup(r0.content.decode("windows-1251","replace"), "html.parser")
    forms = soup0.find_all("form")
    for form in forms[:3]:
        log(f"Form id={form.get('id')} action={form.get('action')}")
        for inp in form.find_all("input"):
            log(f"  <input name={inp.get('name')} type={inp.get('type')} value={inp.get('value','')[:20]}>")
    push()

    # 2. Логин POST
    log("\n=== 2. ЛОГИН ===")
    login_data = {
        "username": NNM_LOGIN,
        "password": NNM_PASSWORD,
        "autologin": "on",
        "login": "Вход",
    }
    r1 = s.post(f"{BASE}/login.php", data=login_data, timeout=20,
        headers={"Referer": f"{BASE}/login.php"})
    log(f"POST: {r1.status_code} → {r1.url}")
    log(f"Cookies: {dict(s.cookies)}")
    
    # Проверяем авторизацию
    soup1 = BeautifulSoup(r1.content.decode("windows-1251","replace"), "html.parser")
    logged = NNM_LOGIN.lower() in r1.text.lower() or "logout" in r1.text.lower()
    profile_link = soup1.find("a", href=lambda h: h and "profile" in str(h))
    log(f"logged={logged}, profile={profile_link}")
    push()

    # 3. Поиск
    log("\n=== 3. ПОИСК 'паша техник' ===")
    q = urllib.parse.quote("паша техник")
    r2 = s.get(f"{BASE}/tracker.php?nm={q}", timeout=20,
        headers={"Referer": f"{BASE}/index.php"})
    log(f"Search: {r2.status_code} → {r2.url}")
    
    soup2 = BeautifulSoup(r2.content.decode("windows-1251","replace"), "html.parser")
    
    # Все таблицы на странице
    tables = soup2.find_all("table")
    log(f"Таблиц: {len(tables)}")
    for t in tables[:8]:
        rows = t.find_all("tr")
        log(f"  table id={t.get('id')} class={t.get('class')} rows={len(rows)}")
    push()

    # Ищем таблицу с результатами
    # Попробуем разные варианты
    result_tbl = None
    for selector in ["table.forumline", "table#tor-tbl", "table.tablesorter"]:
        tbl = soup2.select_one(selector)
        if tbl:
            rows = tbl.find_all("tr")
            log(f"\nНашли {selector}: {len(rows)} строк")
            result_tbl = tbl
            break

    if result_tbl:
        # Показываем первую строку с данными
        data_rows = [r for r in result_tbl.find_all("tr") if len(r.find_all("td")) > 3]
        log(f"Строк с данными: {len(data_rows)}")
        if data_rows:
            log(f"\nПервая строка HTML:")
            log(str(data_rows[0])[:800])
    else:
        # Ищем любые ссылки на темы
        topic_links = soup2.select("a[href*='viewtopic']")
        log(f"\nСсылок viewtopic: {len(topic_links)}")
        for a in topic_links[:5]:
            log(f"  {a.text.strip()[:60]} → {a.get('href','')[:60]}")
        
        # Фрагмент страницы
        log(f"\nФрагмент страницы (1000-2000):")
        log(r2.content.decode("windows-1251","replace")[1000:2000])
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
