#!/usr/bin/env python3
"""Тест NNM-Club: логин + поиск + парсинг + RuTor магнеты"""
import requests, urllib.parse, json, base64, traceback
import urllib.request as ur
from bs4 import BeautifulSoup

LOGIN    = __import__("os").environ.get("RUTRACKER_LOGIN","")
PASSWORD = __import__("os").environ.get("RUTRACKER_PASSWORD","")
GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO     = "Komsomol39/rustream"

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

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

# ===================== NNM-CLUB =====================
log("=== NNM-CLUB: Логин ===")
try:
    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})

    # Получаем форму логина
    r0 = s.get("https://nnmclub.to/forum/login.php", timeout=15)
    log(f"GET login page: {r0.status_code}")
    soup0 = BeautifulSoup(r0.content.decode("windows-1251","replace"), "html.parser")
    form = soup0.find("form", id="login") or soup0.find("form", action=lambda a: a and "login" in str(a))
    if form:
        log(f"Form action: {form.get('action')}")
        for inp in form.find_all("input"):
            if inp.get("type") != "hidden":
                log(f"  <input name={inp.get('name')} type={inp.get('type')}>")
    push()

    # Логин POST
    r1 = s.post("https://nnmclub.to/forum/login.php", data={
        "username": LOGIN,
        "password": PASSWORD,
        "autologin": "on",
        "login": "Вход"
    }, timeout=15, headers={"Referer": "https://nnmclub.to/forum/login.php"})
    log(f"POST login: {r1.status_code} → {r1.url}")
    logged = "logout" in r1.text.lower() or LOGIN.lower() in r1.text.lower()
    log(f"Cookies: {list(s.cookies.keys())}")
    log(f"Залогинен: {logged}")
    push()

    if logged:
        # Поиск
        log("\n=== NNM-CLUB: Поиск 'паша техник' ===")
        q = urllib.parse.quote("паша техник")
        r2 = s.get(f"https://nnmclub.to/forum/tracker.php?nm={q}", timeout=15,
            headers={"Referer": "https://nnmclub.to/forum/index.php"})
        log(f"Search: {r2.status_code}")
        soup2 = BeautifulSoup(r2.content.decode("windows-1251","replace"), "html.parser")

        # Ищем таблицу результатов
        tbl = soup2.find("table", id="tor-tbl") or soup2.find("table", class_="forumline")
        log(f"Таблица: {tbl.get('id') if tbl else 'не найдена'}")

        if tbl:
            rows = tbl.select("tr.tCenter, tr.prow1, tr.prow2")
            log(f"Строк: {len(rows)}")
            for row in rows[:5]:
                title_el = row.select_one("a.tLink, td.torTopic a, a[href*=viewtopic]")
                size_el  = row.select_one("td.tor-size, td.torSize")
                seeds_el = row.select_one("b.seedmed, td.seedmed b, span.seedmed")
                dl_el    = row.select_one("a.tr-dl, a[href*=download]")
                if title_el:
                    log(f"  [{seeds_el.text.strip() if seeds_el else '?'}s] "
                        f"{title_el.text.strip()[:65]} | "
                        f"{size_el.text.strip() if size_el else '?'} | "
                        f"dl={dl_el.get('href','')[:40] if dl_el else 'нет'}")
        else:
            # Диагностика
            tables = soup2.find_all("table")
            log(f"Все таблицы: {[t.get('id') or t.get('class') for t in tables[:6]]}")
            links = [a["href"] for a in soup2.select("a[href*=viewtopic]")[:5]]
            log(f"Ссылки viewtopic: {links}")
        push()

except Exception as e:
    log(f"NNM ERROR: {e}"); log(traceback.format_exc()); push()

# ===================== RUTOR =====================
log("\n=== RUTOR: Магнет-ссылки ===")
try:
    rs = requests.Session()
    rs.headers.update({"User-Agent": UA})

    for mirror in ["https://rutor.info", "https://rutor.is"]:
        try:
            q = urllib.parse.quote("паша техник")
            r3 = rs.get(f"{mirror}/search/0/0/0/{q}", timeout=12)
            log(f"{mirror}: {r3.status_code} len={len(r3.text)}")
            soup3 = BeautifulSoup(r3.text, "html.parser")
            tbl3  = soup3.find("table", id="index")
            rows3 = tbl3.select("tr")[1:] if tbl3 else []
            log(f"  table#index rows={len(rows3)}")

            if rows3:
                for row in rows3[:3]:
                    cells = row.select("td")
                    if len(cells) < 4: continue
                    title  = cells[1].select("a")[-1].text.strip()[:60] if cells[1].select("a") else "?"
                    magnet = cells[1].select_one("a[href^='magnet:']")
                    seeds  = cells[4].select_one("span") if len(cells) > 4 else None
                    log(f"  [{seeds.text.strip() if seeds else '?'}s] {title} | magnet={'✅' if magnet else '❌'}")
                break
            else:
                # Покажем фрагмент для диагностики
                log(f"  Фрагмент: {r3.text[200:600]}")
        except Exception as e:
            log(f"  {mirror}: {e}")
    push()

except Exception as e:
    log(f"RUTOR ERROR: {e}"); log(traceback.format_exc()); push()
