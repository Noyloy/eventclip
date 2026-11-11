#!/usr/bin/env python3
"""Static HELD artifact audit only. Does NOT install/run/upload anything."""
from pathlib import Path
import os,hashlib,json,re,subprocess,zipfile,sys
r=Path(__file__).resolve().parents[1];tools=Path(os.environ.get('ANDROID_HOME',str(r/'.tools/android-sdk')))/'build-tools/37.0.0'
app,test=map(Path,sys.argv[1:3]);vc5=r/'reports/EventClipSpike-offline-arm64-vc5-v1.4.0-diagnostic-HELD-final.apk'
tag=sys.argv[3] if len(sys.argv)>3 else 'vc6'
assert tag in ['vc6','vc7','vc8','vc9','vc10']
code,version={'vc6':('6','1.5.0-private'),'vc7':('7','1.5.1-private'),'vc8':('8','1.5.1-private'),'vc9':('9','1.5.1-private'),'vc10':('10','1.5.1-private')}[tag]
def run(*args):return subprocess.check_output([str(a) for a in args],text=True)
def identity(p):
 badging=run(tools/'aapt','dump','badging',p);cert=run(tools/'apksigner','verify','--print-certs',p)
 return {'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size,'badging':badging,'certificateSha256':re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',cert).group(1),'signatureVerified':True}
a=identity(app);t=identity(test);old=identity(vc5)
assert a['bytes']<24000000
assert a['certificateSha256']==t['certificateSha256']==old['certificateSha256']
assert f"package: name='com.eventclip.spike' versionCode='{code}' versionName='{version}'" in a['badging']
assert "native-code: 'arm64-v8a'" in a['badging'] and 'x86' not in a['badging']
for p in [app,test]:subprocess.run(['python3',str(r/'scripts/audit-private-apk.py'),str(p)],check=True)
manifest=run(tools/'aapt','dump','xmltree',app,'AndroidManifest.xml')
test_manifest=run(tools/'aapt','dump','xmltree',test,'AndroidManifest.xml')
assert 'targetPackage' in test_manifest and 'com.eventclip.spike' in test_manifest and 'androidx.test.runner.AndroidJUnitRunner' in test_manifest
assert 'ProductSurfaceSmokeActivity' in manifest
assert 'requestLegacyExternalStorage' not in manifest
resources=run(tools/'aapt','dump','--values','resources',app)
def xml_path(name):
 match=re.search(r'resource 0x[0-9a-f]+ com\.eventclip\.spike:xml/'+name+r':[^\n]*\n\s+\(string8\) "([^"]+)"',resources)
 assert match,name
 return match.group(1)
with zipfile.ZipFile(app) as z:
 bundle=z.read('assets/index.android.bundle')
 assert b'EVENTCLIP_PRODUCT_VC6_PRIVATE_ENTRY' in bundle, 'Wrong/missing embedded product JS entry marker'
 # Own native APIs kept for SAME minified release instrumentation, not a debug fallback.
 dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
 for name in [b'BirthdayImportsModule',b'ProductExportController',b'ProductNativeComposition',b'ProductAudioDecoder',b'ProductSaveTransaction',b'ProductPreviewView']:assert name in dex,name
 forbidden=[b'Lcom/google/firebase/',b'Lio/sentry/',b'Lcom/bugsnag/',b'Lcom/segment/analytics/',b'Lcom/amplitude/',b'Lcom/appsflyer/',b'Lcom/adjust/sdk/']
 assert not any(prefix in dex for prefix in forbidden)
 assert not any('product-m06/' in n for n in z.namelist()), 'Test fixtures leaked into user APK'
 backups={}
 for resource in ['birthday_backup_rules','birthday_data_extraction_rules']:
  n=xml_path(resource);tree=run(tools/'aapt','dump','xmltree',app,n);assert 'eventclip-birthday/' in tree and 'eventclip-birthday-exports/' in tree;backups[n]=tree
 paths=run(tools/'aapt','dump','xmltree',app,xml_path('file_paths'));assert 'eventclip-birthday-exports/' in paths
with zipfile.ZipFile(test) as z:
 assert 'assets/product-m06/provenance.json' in z.namelist() and 'assets/product-m06/gapless-provenance.json' in z.namelist()
 dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
 assert b'ProductM06InstrumentedTest' in dex and b'NativePcmEvidence' in dex
 assert b'FullScenarioInstrumentedTest' not in dex and b'Full1080OnlyInstrumentedTest' not in dex
maps=list((r/'app/android/app/build/generated/sourcemaps').rglob('*.map'))
entry_sources=[]
for f in maps:
 try:d=json.loads(f.read_text())
 except (json.JSONDecodeError,UnicodeError):continue
 sources=d.get('sources',[])
 hits=[s for s in sources if s.endswith(('index.product.ts','App.realmedia.foundation.tsx','src/realmedia/ImportReview.tsx'))]
 if hits:
  assert not any(s.endswith('/app/App.tsx') or s in ['App.tsx','/App.tsx'] for s in sources)
  entry_sources=hits;break
assert entry_sources,'Missing independent Metro source-map entry verification'
output={'status':'HELD_RUNTIME_UNVALIDATED','app':a,'test':t,'historicalVc5':{'sha256':old['sha256'],'bytes':old['bytes'],'certificateSha256':old['certificateSha256']},'testTarget':'com.eventclip.spike release, same minified release build; no debug substitute','productEmbeddedEntry':True,'productMetroSources':entry_sources,'testFixturesOnlyInTestApk':True,'backupsVerified':True,'forbiddenPermissionsBothApks':'PASS','knownAnalyticsCrashSdkPrefixesAbsent':True,'androidTestsExecuted':False,'lint':'NOT RUN: missing offline library lint runtime versions; explicit excluded tasks recorded in build log'}
(r/f'reports/{tag}-artifact-audit.json').write_text(json.dumps(output,indent=2))
(r/f'reports/{tag}-app-manifest.txt').write_text(manifest);(r/f'reports/{tag}-test-manifest.txt').write_text(test_manifest)
(r/f'reports/{tag}-backup-provider-audit.json').write_text(json.dumps({'backupXml':backups,'fileProviderXml':paths},indent=2))
print(json.dumps(output,indent=2))
