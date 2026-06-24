#!/usr/bin/env python3
import requests, os, urllib.parse, traceback, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

LOGIN    = os.environ.get("RUTRACKER_LOGIN","")
PASSWORD = os.environ.get("RUTRACKER_PASSWORD","")
GH_TOKEN = os.environ.get("GITHUB_TOKEN","")
REPO     = "Komsomol39/rustream"
BASE     = "https://rutracker.net/forum"

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
    s.headers.update({
        "User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
        "Accept-Language":"ru-RU,ru;q=0.9",
    })

    # Получить форму логина
    log("=== 1. ФОРМА ЛОГИНА ===")
    r0 = s.get(f"{BASE}/index.php", timeout=20)
    log(f"GET index: {r0.status_code}")
    soup0 = BeautifulSoup(r0.text, "html.parser")
    form = soup0.find("form", id="login-form") or soup0.find("form", action=lambda a: a and "login" in str(a))
    if form:
        log(f"Form action: {form.get('action')}")
        for inp in form.find_all("input"):
            log(f"  <input name='{inp.get('name')}' type='{inp.get('type')}' value='{inp.get('value','')[:20]}'>")
    else:
        log("Форма не найдена! Ищем все формы:")
        for f2 in soup0.find_all("form")[:5]:
            log(f"  form id={f2.get('id')} action={f2.get('action')}")
    push()

    # Логин с правильными данными формы
    log("\n=== 2. ЛОГИН ===")
    login_data = {
        "login_username": LOGIN,
        "login_password": PASSWORD,
        "login": "Вход",
    }
    if form:
        # Добавляем все hidden поля
        for inp in form.find_all("input", type="hidden"):
            if inp.get("name"):
                login_data[inp["name"]] = inp.get("value","")
                log(f"  hidden: {inp['name']}={inp.get('value','')[:30]}")
    
    action = form.get("action","login.php") if form else "login.php"
    login_url = f"{BASE}/{action.lstrip('/')}" if not action.startswith("http") else action
    log(f"POST to: {login_url}")
    
    r1 = s.post(login_url, data=login_data, timeout=25,
        headers={"Referer": f"{BASE}/index.php"})
    log(f"  {r1.status_code} → {r1.url}")
    log(f"  cookies: {dict(s.cookies)}")
    
    # Проверка авторизации — смотрим наличие профиля
    soup1 = BeautifulSoup(r1.text, "html.parser")
    profile = soup1.select_one("#logged-in-username, .logged-in, a[href*=profile]")
    log(f"  profile element: {profile}")
    # Проверяем через отдельный GET
    r_check = s.get(f"{BASE}/index.php", timeout=20)
    soup_check = BeautifulSoup(r_check.text, "html.parser")
    logged_el = soup_check.select_one(".logged-in") or soup_check.find(string=lambda t: t and LOGIN.lower() in t.lower())
    log(f"  logged check: {'✅ залогинен' if logged_el else '❌ не залогинен'}")
    # Фрагмент страницы для диагностики
    log(f"  Фрагмент после логина (1500-2000): {r1.text[1500:2000]}")
    push()

    # Поиск
    log("\n=== 3. ПОИСК 'sting' ===")
    r2 = s.get(f"{BASE}/tracker.php?nm=sting", timeout=25,
        headers={"Referer": f"{BASE}/index.php"})
    log(f"  {r2.status_code} → {r2.url}")
    soup2 = BeautifulSoup(r2.text, "html.parser")
    tbl = soup2.find("table", id="tor-tbl")
    rows = tbl.select("tbody tr.tCenter") if tbl else []
    log(f"  tbl={'✅' if tbl else '❌'} rows={len(rows)}")
    if not tbl:
        # Нет таблицы — показываем фрагмент страницы
        log(f"  Фрагмент поиска: {r2.text[500:1500]}")
    for row in rows[:3]:
        title_el = row.select_one("a.tLink")
        seeds_el = row.select_one("b.seedmed")
        size_el  = row.select_one("td.tor-size")
        log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] {title_el.text.strip()[:65] if title_el else '?'} | {size_el.text.strip() if size_el else '?'}")
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
