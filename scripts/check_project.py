#!/usr/bin/env python3
"""Source, resource and privacy checks (run before the Gradle build; see docs/TESTING.md)."""
import os, re, sys, subprocess, xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors = []
def err(m): errors.append(m)
def files(base, exts):
    for d, _, fs in os.walk(os.path.join(ROOT, base)):
        if "/build" in d: continue
        for f in fs:
            if f.endswith(exts): yield os.path.join(d, f)

# 1. No secrets / signing material / personal SMS backups tracked
try:
    tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True).stdout.split()
except Exception:
    tracked = []
for t in tracked:
    if re.search(r"\.(jks|keystore|env|bsms)$|signing\.properties$|sms-backup.*\.xml$", t): err(f"sensitive file tracked: {t}")

# 2. Money must never use floating point in the financial engine
for f in files("core/domain/src/main", (".kt",)):
    for i, line in enumerate(open(f, encoding="utf-8"), 1):
        if "appearance" in f: continue
        code = line.split("//")[0]
        if code.strip().startswith(("*", "/*")): continue
        if re.search(r"\b(Float|Double)\b", code):
            err(f"floating point in financial engine: {f}:{i}")

# 3. No logging of message bodies or amounts; no network permission (offline-first)
for f in files("app/src/main", (".kt",)):
    src = open(f, encoding="utf-8").read()
    if re.search(r"Log\.[dviwe]\(.*(body|amount|balance)", src, re.I): err(f"possible sensitive log in {f}")
manifest = open(os.path.join(ROOT, "app/src/main/AndroidManifest.xml"), encoding="utf-8").read()
for p in re.findall(r'uses-permission android:name="([^"]+)"', manifest):
    if p not in {"android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.POST_NOTIFICATIONS", "android.permission.USE_BIOMETRIC"}:
        err(f"unexpected permission: {p}")
if "android.permission.SEND_SMS" in manifest or "android.permission.INTERNET" in manifest: err("SEND_SMS / INTERNET must not be requested")

# 4. Arabic and English strings are complete and their format arguments match
def strings(path):
    root = ET.parse(os.path.join(ROOT, path)).getroot()
    return {e.get("name"): (e.text or "") for e in root.findall("string")}
en = strings("app/src/main/res/values/strings.xml"); ar = strings("app/src/main/res/values-ar/strings.xml")
for k in sorted(set(en) ^ set(ar)): err(f"string not in both languages: {k}")
for k in en:
    if k in ar and sorted(re.findall(r"%\d\$[sd]", en[k])) != sorted(re.findall(r"%\d\$[sd]", ar[k])): err(f"format args differ: {k}")
used = set()
for f in files("app/src/main", (".kt",)): used |= set(re.findall(r"R\.string\.(\w+)", open(f, encoding="utf-8").read()))
for k in sorted(used - set(en)): err(f"missing string resource: {k}")

# 5. No hard-coded user-facing text in Compose Text("...") calls (letters only; symbols allowed)
for f in files("app/src/main/kotlin", (".kt",)):
    for i, line in enumerate(open(f, encoding="utf-8"), 1):
        for m in re.finditer(r'Text\("([^"$]*)"', line):
            if re.search(r"[A-Za-z؀-ۿ]{3,}", m.group(1)): err(f"hard-coded text {f}:{i}: {m.group(1)}")

# 6. Room schema export directory exists (schemas are committed after the first CI build)
if not os.path.isdir(os.path.join(ROOT, "core/data/schemas")): err("core/data/schemas missing")

if errors:
    print("\n".join("✗ " + e for e in errors)); sys.exit(1)
print("✓ project checks passed")
