#!/usr/bin/env python3
"""Static DEX class-definition/reference closure. Not execution or reflective-linkage proof."""
import argparse,struct,zipfile,json
from pathlib import Path

def dex(data,details=False):
 u=lambda o:struct.unpack_from('<I',data,o)[0]
 def string(off):
  while data[off]&128:off+=1
  off+=1
  return data[off:data.index(b'\0',off)].decode('utf-8','replace')
 strings=[string(u(u(60)+i*4)) for i in range(u(56))]
 types=[strings[u(u(68)+i*4)] for i in range(u(64))]
 defined={types[u(u(100)+i*32)] for i in range(u(96))}
 if not details:return defined,set(types)
 def leb(off):
  n=0;shift=0
  while True:
   b=data[off];off+=1;n|=(b&127)<<shift
   if b<128:return n,off
   shift+=7
 protos=[]
 for i in range(u(72)):
  off=u(76)+i*12;params=u(off+8)
  args=[] if not params else [types[struct.unpack_from('<H',data,params+4+j*2)[0]] for j in range(u(params))]
  protos.append('('+''.join(args)+')'+types[u(off+4)])
 methods=[]
 for i in range(u(88)):
  off=u(92)+i*8;cl,pr,st=struct.unpack_from('<HHI',data,off);methods.append((types[cl],strings[st]+protos[pr]))
 info={}
 for i in range(u(96)):
  off=u(100)+i*32;cl=types[u(off)];sup=u(off+8);it=u(off+12);cd=u(off+24)
  parents=([types[sup]] if sup!=0xffffffff else [])+([] if not it else [types[struct.unpack_from('<H',data,it+4+j*2)[0]] for j in range(u(it))])
  declared=set()
  if cd:
   counts=[]
   for _ in range(4):v,cd=leb(cd);counts.append(v)
   for _ in range(counts[0]+counts[1]):_,cd=leb(cd);_,cd=leb(cd)
   for count in counts[2:]:
    idx=0
    for _ in range(count):
     d,cd=leb(cd);idx+=d;_,cd=leb(cd);_,cd=leb(cd);declared.add(methods[idx][1])
  info[cl]={'parents':parents,'methods':declared}
 return defined,set(types),info,set(methods)

def apk(path):
 defs=set();refs=set()
 with zipfile.ZipFile(path) as z:
  for n in z.namelist():
   if n.startswith('classes') and n.endswith('.dex'):
    d,r=dex(z.read(n));defs|=d;refs|=r
 return defs,refs

def method_boundary(app,test,boundary):
 info={};testrefs=set()
 for path,is_test in [(app,False),(test,True)]:
  with zipfile.ZipFile(path) as z:
   for n in z.namelist():
    if n.startswith('classes') and n.endswith('.dex'):
     _,_,classes,methods=dex(z.read(n),True);info.update(classes)
     if is_test:testrefs|=methods
 required={'L'+n+';' for n in boundary}
 def has(cl,method,seen):
  if cl in seen:return False
  seen.add(cl)
  if cl in info:
   if method in info[cl]['methods']:return True
   return any(has(p,method,seen) for p in info[cl]['parents'])
  # Java Object methods are runtime SDK members, not app ABI.
  return cl=='Ljava/lang/Object;' and method in {'toString()Ljava/lang/String;','hashCode()I','equals(Ljava/lang/Object;)Z','getClass()Ljava/lang/Class;','clone()Ljava/lang/Object;'}
 checked=sorted((cl,m) for cl,m in testrefs if cl in required)
 return {'requiredClasses':len(required),'missingRequiredClasses':sorted(required-info.keys()),'checkedTestMethodReferences':len(checked),'missingBoundaryMethods':[(cl,m) for cl,m in checked if not has(cl,m,set())]}

def check(app,test,android):
 ad,ar=apk(app);td,tr=apk(test)
 with zipfile.ZipFile(android) as z:boot={'L'+n[:-6]+';' for n in z.namelist() if n.endswith('.class')}
 normalize=lambda t:t.lstrip('[')
 refs={normalize(t) for t in ar|tr if normalize(t).startswith('L')}
 missing=sorted(refs-ad-td-boot)
 # SDK stubs omit several platform internals referenced by installed libraries.
 # Keep these visible, never pretend the SDK inventory is the actual runtime bootclasspath.
 hidden={'Lsun/misc/Unsafe;','Ljava/lang/invoke/StringConcatFactory;','Ljava/lang/invoke/LambdaMetafactory;'}
 required=[s for s in missing if s not in hidden]
 out={'scope':'DEX type references across both APKs vs definitions and Android SDK stubs; no runtime/reflective/method-linkage claim','appDefinitions':len(ad),'testDefinitions':len(td),'duplicateDefinitions':sorted(ad&td),'missingSdkInventoryTypes':missing,'missingRequiredTypes':required,'runnerPresent':'Landroidx/test/runner/AndroidJUnitRunner;' in td,'traceOriginalNamePresent':'Landroidx/tracing/Trace;' in ad|td,'pass':not required and 'Landroidx/test/runner/AndroidJUnitRunner;' in td and 'Landroidx/tracing/Trace;' in ad|td}
 return out
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('app');p.add_argument('test');p.add_argument('--android-jar',required=True);p.add_argument('--out',required=True);p.add_argument('--boundary');a=p.parse_args();out=check(a.app,a.test,a.android_jar);
 if a.boundary:
  boundary=json.loads(Path(a.boundary).read_text())['sharedClasses'];out['runnerBoundary']=method_boundary(a.app,a.test,boundary);out['pass']=not out['runnerBoundary']['missingRequiredClasses'] and not out['runnerBoundary']['missingBoundaryMethods'] and out['runnerPresent'] and out['traceOriginalNamePresent'];out['passScope']='Runner-exclusive and instrumentation-owned shared-ABI class/test-method references plus AndroidX test/tracing/concurrent and Kotlin type closure; not runtime execution or all optional platform types';out['unresolvedStartupLibraryTypes']=[t for t in out['missingSdkInventoryTypes'] if t.startswith(('Landroidx/test/','Landroidx/tracing/','Landroidx/concurrent/','Lkotlin/','Lkotlinx/'))];out['pass']=out['pass'] and not out['unresolvedStartupLibraryTypes'];out.pop('missingRequiredTypes',None)
 Path(a.out).write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2));raise SystemExit(0 if out['pass'] else 1)
