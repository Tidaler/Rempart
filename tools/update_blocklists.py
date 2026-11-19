#!/usr/bin/env python3
"""Met à jour les listes de blocage embarquées dans l'APK.

Usage : python tools/update_blocklists.py
Sources (licences compatibles GPL-3.0, voir NOTICE.md) :
  - HaGeZi NSFW (GPL-3.0)
  - StevenBlack hosts « porn-only » (MIT, données Clefspeare13 / Sinfonietta…)
  - HaGeZi Encrypted DNS Bypass (GPL-3.0)
Le résultat est trié, dédoublonné et compressé en gzip (sortie déterministe).
"""
import gzip, io, pathlib, re, sys, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "filter-dns" / "src" / "main" / "assets" / "blocklists"
NSFW = [
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/nsfw-onlydomains.txt",
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
]
DOH = ["https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/doh-onlydomains.txt"]
DOMAIN = re.compile(r"^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+$")
SKIP = {"localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0"}

def parse(text):
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip().lower()
        if not line or line.startswith("!"):
            continue
        parts = line.split()
        d = parts[1] if len(parts) >= 2 and parts[0] in ("0.0.0.0", "127.0.0.1", "::") else parts[0]
        d = d.removeprefix("||").removesuffix("^").removeprefix("*.").strip(".")
        if d not in SKIP and DOMAIN.match(d):
            yield d

def fetch(url, cache):
    path = cache / re.sub(r"[^a-z0-9]+", "_", url.lower())
    if len(sys.argv) > 1 and sys.argv[1] == "--cached" and path.exists():
        return path.read_text(encoding="utf-8")
    req = urllib.request.Request(url, headers={"User-Agent": "Rempart-build"})
    text = urllib.request.urlopen(req, timeout=120).read().decode("utf-8", "replace")
    path.write_text(text, encoding="utf-8")
    return text

def write(name, urls, cache, minimum):
    domains = sorted({d for u in urls for d in parse(fetch(u, cache))})
    if len(domains) < minimum:
        sys.exit(f"{name}: seulement {len(domains)} domaines, abandon")
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(("\n".join(domains) + "\n").encode())
    (OUT / name).write_bytes(buf.getvalue())
    print(f"{name}: {len(domains)} domaines, {len(buf.getvalue()) // 1024} Kio")

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    cache = ROOT / "tools" / ".cache"; cache.mkdir(exist_ok=True)
    write("nsfw.txt.gzip", NSFW, cache, 50_000)  # pas .gz : AGP décompresse les assets .gz
    write("doh.txt.gzip", DOH, cache, 1_000)
