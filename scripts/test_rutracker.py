#!/usr/bin/env python3
"""Разбираем структуру NNM RSS — что есть в торрент-элементах vs новостях"""
import requests, json, base64, traceback
import urllib.request as ur
from xml.etree import ElementTree as ET

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"

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
    except: pass

try:
    s = requests.Session()
    s.headers.update({"User-Agent":"Mozilla/5.0"})

    # 1. Смотрим что есть в элементах RSS для "Паша Техник"
    log("=== RSS элементы для 'паша техник' ===")
    import urllib.parse
    r = s.get(f"https://nnmclub.to/forum/rss.php?nm={urllib.parse.quote('паша техник')}", timeout=15)
    root = ET.fromstring(r.content.decode("windows-1251", errors="replace").encode("utf-8"))
    
    items = root.findall(".//item")
    log(f"Всего items: {len(items)}")
    
    has_enclosure = 0
    no_enclosure = 0
    for item in items:
        title = item.findtext("title","")
        link  = item.findtext("link","")
        enc   = item.find("enclosure")
        cat   = item.findtext("category","")
        desc  = item.findtext("description","")[:80] if item.findtext("description") else ""
        
        if enc is not None:
            has_enclosure += 1
            size = int(enc.get("length",0))//1024//1024
            log(f"  [TORRENT] {title[:60]} | cat={cat} | {size}MB")
        else:
            no_enclosure += 1
            if no_enclosure <= 5:
                log(f"  [NEWS]    {title[:60]} | cat={cat}")
    
    log(f"\nИтого: {has_enclosure} торрентов, {no_enclosure} новостей/прочего")
    push()

    # 2. Проверим RSS с параметром категории
    log("\n=== RSS с фильтром категорий ===")
    # NNM поддерживает параметр c= для категорий
    # Попробуем разные варианты фильтрации
    test_urls = [
        f"https://nnmclub.to/forum/rss.php?nm={urllib.parse.quote('паша техник')}&c=1",   # музыка?
        f"https://nnmclub.to/forum/rss.php?nm={urllib.parse.quote('паша техник')}&f=1",   # с файлом?
        f"https://nnmclub.to/forum/rss.php?nm={urllib.parse.quote('паша техник')}&dl=1",  # downloads?
    ]
    for url in test_urls:
        try:
            r2 = s.get(url, timeout=10)
            root2 = ET.fromstring(r2.content.decode("windows-1251", errors="replace").encode("utf-8"))
            items2 = root2.findall(".//item")
            with_enc = sum(1 for i in items2 if i.find("enclosure") is not None)
            log(f"  {url[-30:]}: {len(items2)} items, {with_enc} с enclosure")
        except Exception as e:
            log(f"  ERROR: {e}")
    push()

    # 3. Смотрим все теги внутри торрент-элемента
    log("\n=== Полная структура торрент-элемента ===")
    tor_item = next((i for i in items if i.find("enclosure") is not None), None)
    if tor_item:
        for child in tor_item:
            log(f"  <{child.tag}> = {child.text[:80] if child.text else ''} attrib={child.attrib}")
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
