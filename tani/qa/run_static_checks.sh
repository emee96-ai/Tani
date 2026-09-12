#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

pass(){ printf 'PASS: %s\n' "$1"; }
fail(){ printf 'FAIL: %s\n' "$1" >&2; exit 1; }

bash -n gradlew || fail "gradlew shell syntax"
pass "gradlew shell syntax"
jar tf gradle/wrapper/gradle-wrapper.jar >/dev/null || fail "Gradle wrapper JAR"
pass "Gradle wrapper JAR"
node --check admin/app.js >/dev/null || fail "Admin JavaScript syntax"
pass "Admin JavaScript syntax"

python3 - <<'PY'
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
root=Path('.')
errors=[]

def ok(label, detail=''):
    print(f"PASS: {label}" + (f" ({detail})" if detail else ''))

def bad(label, items):
    errors.append((label,items))
    print(f"FAIL: {label}: {items}")

# XML / manifest parsing
xmls=[root/'app/src/main/AndroidManifest.xml'] + list((root/'app/src/main/res').rglob('*.xml'))
for p in xmls:
    try: ET.parse(p)
    except Exception as e: bad('XML parse', f'{p}: {e}')
if not any(x[0]=='XML parse' for x in errors): ok('XML/Manifest parse', str(len(xmls)))

# Android IDs defined in layouts vs R.id refs.
id_defs=set()
for p in (root/'app/src/main/res').rglob('*.xml'):
    txt=p.read_text(errors='ignore')
    id_defs |= set(re.findall(r'@\+id/([A-Za-z0-9_]+)',txt))
kt='\n'.join(p.read_text(errors='ignore') for p in (root/'app/src/main/java').rglob('*.kt'))
id_refs=set(re.findall(r'(?<!android\.)R\.id\.([A-Za-z0-9_]+)',kt))
missing=sorted(id_refs-id_defs)
if missing: bad('Android R.id references', missing)
else: ok('Android R.id references', str(len(id_refs)))

# Resource references from Kotlin and XML.
res=root/'app/src/main/res'
file_types={'color','layout','drawable','mipmap','menu','xml','font','raw','anim','animator','navigation','transition'}
res_defs={t:set() for t in file_types|{'color','string','style','dimen','integer','bool','array','plurals','attr','id'}}
for t in file_types:
    for d in res.iterdir():
        if d.is_dir() and d.name.split("-")[0] == t:
            res_defs[t] |= {p.stem for p in d.iterdir() if p.is_file()}
for p in (res/'values').glob('*.xml'):
    try:
        tree=ET.parse(p)
        for child in tree.getroot():
            name=child.attrib.get('name')
            if not name: continue
            tag=child.tag.split('}')[-1]
            typ=child.attrib.get('type') if tag=='item' else tag
            if typ in res_defs: res_defs[typ].add(name)
    except Exception: pass
for qdir in [p for p in res.iterdir() if p.is_dir() and p.name.startswith('values-')]:
    for p in qdir.glob('*.xml'):
        try:
            tree=ET.parse(p)
            for child in tree.getroot():
                name=child.attrib.get('name'); tag=child.tag.split('}')[-1]
                typ=child.attrib.get('type') if tag=='item' else tag
                if name and typ in res_defs: res_defs[typ].add(name)
        except Exception: pass
res_defs["style"] |= {name.replace(".", "_") for name in res_defs["style"]}
refs=[]
for m in re.finditer(r'(?<!android\.)R\.(layout|drawable|mipmap|menu|xml|font|raw|anim|animator|navigation|transition|color|string|style|dimen|integer|bool|array|plurals|attr)\.([A-Za-z0-9_]+)',kt):
    refs.append((m.group(1),m.group(2),'Kotlin'))
for p in xmls:
    txt=p.read_text(errors='ignore')
    for m in re.finditer(r'(?<!android:)@(layout|drawable|mipmap|menu|xml|font|raw|anim|animator|navigation|transition|color|string|style|dimen|integer|bool|array|plurals|attr)/([A-Za-z0-9_.]+)',txt):
        
        if '.' not in m.group(2): refs.append((m.group(1),m.group(2),str(p)))
missing_res=sorted({f'{t}/{n}' for t,n,_ in refs if n not in res_defs.get(t,set())})
if missing_res: bad('Android resource references', missing_res[:40])
else: ok('Android resource references', str(len(set((a,b) for a,b,_ in refs))))

# Internal imports should point to a class/object in source except generated R/BuildConfig.
classes={}
for p in (root/'app/src/main/java').rglob('*.kt'):
    txt=p.read_text(errors='ignore')
    pkg=re.search(r'^package\s+([\w.]+)',txt,re.M)
    if not pkg: continue
    for m in re.finditer(r'^(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?(?:class|object|interface)\s+([A-Za-z0-9_]+)',txt,re.M):
        classes[pkg.group(1)+'.'+m.group(1)]=p
imports=set(re.findall(r'^import\s+(com\.tani\.app\.[\w.]+)',kt,re.M))
missing_import=[]
for imp in sorted(imports):
    if imp.endswith('.*') or imp in {'com.tani.app.R','com.tani.app.BuildConfig'}: continue
    if imp not in classes:
        # Imports may target top-level functions/helpers; resolve by source text/package prefix.
        parts=imp.split('.')
        pkg='.'.join(parts[:-1]); name=parts[-1]
        found=False
        for p in (root/'app/src/main/java').rglob('*.kt'):
            txt=p.read_text(errors='ignore')
            if re.search(rf'^package\s+{re.escape(pkg)}\s*$',txt,re.M) and re.search(rf'\b(?:fun|val|var|typealias)\s+{re.escape(name)}\b',txt): found=True; break
        if not found: missing_import.append(imp)
if missing_import: bad('Internal imports',missing_import[:40])
else: ok('Internal imports',str(len(imports)))

# SQL dependency coverage from Kotlin/Admin.
sql='\n'.join(p.read_text(errors='ignore') for p in (root/'supabase').glob('*.sql'))
# definitions
relations=set(re.findall(r'(?i)create\s+table\s+(?:if\s+not\s+exists\s+)?public\.([A-Za-z0-9_]+)',sql))
relations |= set(re.findall(r'(?i)create(?:\s+or\s+replace)?\s+view\s+public\.([A-Za-z0-9_]+)',sql))
relations |= set(re.findall(r'(?i)create\s+or\s+replace\s+view\s+public\.([A-Za-z0-9_]+)',sql))
funcs=set(re.findall(r'(?i)create\s+or\s+replace\s+function\s+public\.([A-Za-z0-9_]+)',sql))
# Calls in Kotlin
kt_rel=set()
kt_rpc=set()
for p in (root/'app/src/main/java').rglob('*.kt'):
    txt=p.read_text(errors='ignore')
    # any literal immediately used as a Supabase route
    for m in re.finditer(r'Supabase\.(?:get|post|patch|delete)(?:<[^>]+>)?\s*\(\s*"([^"]+)"',txt,re.S):
        route=m.group(1)
        if route.startswith('rpc/'): kt_rpc.add(route.split('/',1)[1])
        elif '/' not in route: kt_rel.add(route)
# Admin helpers / endpoints
admin=(root/'admin/app.js').read_text(errors='ignore')
admin_html=(root/'admin/index.html').read_text(errors='ignore')
admin_ids=set(re.findall(r'\bid=["\']([^"\']+)',admin_html))
admin_id_refs=set(re.findall(r'\$\(["\']([^"\']+)',admin))
missing_admin_ids=sorted(admin_id_refs-admin_ids)
if missing_admin_ids: bad('Admin DOM id references',missing_admin_ids)
else: ok('Admin DOM id references',str(len(admin_id_refs)))
for m in re.finditer(r'\b(?:sbGet|sbPost|sbPatch|sbDelete)\(\s*[`\'"]([^`\'"]+)',admin):
    route=m.group(1)
    route=route.split('?')[0]
    if route.startswith('rpc/'): kt_rpc.add(route.split('/',1)[1])
    elif '/' not in route and '${' not in route: kt_rel.add(route)
for m in re.finditer(r'[`\'"]rpc/([A-Za-z0-9_]+)',admin): kt_rpc.add(m.group(1))
for m in re.finditer(r'\b(?:restRows|exactCount|patchRow)\(\s*[`\'"]([A-Za-z0-9_]+)[`\'"]',admin): kt_rel.add(m.group(1))
for m in re.finditer(r'\brpc\(\s*[`\'"]([A-Za-z0-9_]+)[`\'"]',admin): kt_rpc.add(m.group(1))
for m in re.finditer(r'/rest/v1/([A-Za-z0-9_]+)(?:\?|[?`\'"])',admin):
    if m.group(1) != 'rpc': kt_rel.add(m.group(1))
for m in re.finditer(r'/rest/v1/rpc/([A-Za-z0-9_]+)',admin): kt_rpc.add(m.group(1))
missing_tables=sorted(x for x in kt_rel if x not in relations and x not in {'marketplace_product_cards','marketplace_store_cards'})
# views may be created with security_invoker syntax, catch generic create view separately
for x in list(missing_tables):
    if re.search(rf'(?i)create[\s\S]{{0,120}}view\s+public\.{re.escape(x)}\b',sql): missing_tables.remove(x)
missing_rpc=sorted(kt_rpc-funcs)
if missing_tables: bad('SQL table/view dependency coverage',missing_tables)
else: ok('SQL table/view dependency coverage',str(len(kt_rel)))
if missing_rpc: bad('SQL RPC dependency coverage',missing_rpc)
else: ok('SQL RPC dependency coverage',str(len(kt_rpc)))

# Basic SQL structural checks
for p in (root/'supabase').glob('*.sql'):
    txt=p.read_text(errors='ignore')
    if txt.count('$$')%2: bad('SQL dollar-quote balance',p.name)
if not any(x[0]=='SQL dollar-quote balance' for x in errors): ok('SQL dollar-quote balance')

# Secret scan: publishable keys are allowed; privileged keys are not.
scan_files=[p for p in list((root/'app').rglob('*'))+list((root/'admin').rglob('*')) if p.suffix.lower() not in {'.md','.txt'}]
secret_hits=[]
patterns=[r'(?i)service[_ -]?role',r'(?i)supabase_service_role',r'\bsk_[A-Za-z0-9_-]{16,}\b',r'(?i)private[_ -]?key']
for p in scan_files:
    if not p.is_file(): continue
    try: txt=p.read_text(errors='ignore')
    except Exception: continue
    for pat in patterns:
        if re.search(pat,txt): secret_hits.append(str(p)); break
if secret_hits: bad('Privileged secret scan',sorted(set(secret_hits)))
else: ok('Privileged secret scan')

# Unfinished/debug placeholders.
unfinished=[]
for p in list((root/'app/src/main/java').rglob('*.kt'))+list((root/'admin').rglob('*.js')):
    txt=p.read_text(errors='ignore')
    if re.search(r'\b(TODO|FIXME|NotImplementedError)\b|TODO\(',txt): unfinished.append(str(p))
if unfinished: bad('Unfinished-code scan',unfinished)
else: ok('Unfinished-code scan')

# Known regression checks.
if '.commit().commit()' in kt: bad('Fragment transaction regression','.commit().commit()')
else: ok('Fragment transaction regression')
if 'arguments = null' in kt: bad('Fragment argument mutation','arguments = null')
else: ok('Fragment argument mutation')

manifest=(root/'app/src/main/AndroidManifest.xml').read_text(errors='ignore')
if 'android:allowBackup="false"' not in manifest: bad('Manifest backup security','allowBackup must be false')
else: ok('Manifest backup security')
if 'android:usesCleartextTraffic="false"' not in manifest: bad('Manifest cleartext security','usesCleartextTraffic must be false')
else: ok('Manifest cleartext security')

# Required launch docs.
required={
 'TERMS_AND_PRIVACY_DRAFT.md','MERCHANT_AGREEMENT_DRAFT.md','REFUND_CANCELLATION_POLICY_DRAFT.md',
 'DELIVERY_POLICY_DRAFT.md','REVIEW_POLICY_DRAFT.md','PROHIBITED_PRODUCTS_POLICY_DRAFT.md',
 'COMPLAINT_DISPUTE_POLICY_DRAFT.md','DATA_RETENTION_DELETION_POLICY_DRAFT.md',
 'OPERATIONS_PLAYBOOK.md','BACKUP_RECOVERY_RUNBOOK.md','RELEASE_CHECKLIST.md'
}
existing={p.name for p in (root/'docs').glob('*.md')}
missing_docs=sorted(required-existing)
if missing_docs: bad('Launch documents',missing_docs)
else: ok('Launch documents',str(len(required)))

if errors:
    print(f"\nQA FAILED: {len(errors)} category/categories")
    sys.exit(1)
print('\nSTATIC QA PASSED')
PY
