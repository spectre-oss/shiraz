# SPDX-License-Identifier: GPL-3.0-only
import os
import re,json,pathlib,glob,subprocess,shlex
R=pathlib.Path(__file__).resolve().parents[2]; S=R/'context/digitalis'; W=R/'build/digitalis-host'; mods={}
for bp in S.rglob('Android.bp'):
 t=bp.read_text(); t=re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',t)
 toks=re.findall(r'"(?:\\.|[^"\\])*"|[A-Za-z_][\w.]*|[{}\[\]:,+=(\)]|\d+',t); i=0
 def val():
  global i
  x=toks[i];i+=1
  if x in ('{','['):
   out={} if x=='{' else [];end='}' if x=='{' else ']'
   while toks[i]!=end:
    if x=='{':
     k=toks[i];i+=2;out[k]=val()
    else:out.append(val())
    if toks[i]==',':i+=1
   i+=1;return out
  v=json.loads(x) if x.startswith('"') or x in ('true','false') or x.isdigit() else x
  if i<len(toks) and toks[i]=='+':i+=1;v+=val()
  return v
 while i<len(toks):
  typ=toks[i];i+=1
  if toks[i]!='{':
   print('unsupported',bp,typ,toks[i]);break
  m=val()
  if 'name'in m:mods[m['name']]=(typ,m,bp.parent)
def merge(a,b):
 for k,v in b.items():
  if isinstance(v,list):a[k]=a.get(k,[])+v
  elif isinstance(v,dict):a[k]=merge(a.get(k,{}),v)
  else:a[k]=v
 return a
cache={}
def config(n):
 if n in cache:return cache[n]
 typ,m,p=mods[n];d={}
 for base in m.get('defaults',[]):
  if base in mods:merge(d,config(base))
 own={k:v for k,v in m.items() if k not in ('defaults','arch','target')}; merge(d,own)
 for v in (m.get('arch',{}).get('x86_64',{}),m.get('target',{}).get('host',{}),m.get('target',{}).get('linux',{}),m.get('target',{}).get('linux_glibc',{})):merge(d,v)
 # Resolve paths relative to their declaring module, before inheritance.
 # Inherited paths are already absolute.
 for key in ('srcs','export_include_dirs','local_include_dirs'):
  d[key]=[x if x.startswith(('/',':')) else str(p/x) for x in d.get(key,[])]
 cache[n]=d;return d
seen=set();sources=set();includes={str(S),str(W/'gen')};gen=set();external=set()
def files(items):
 result=[]
 for x in items:
  if x.startswith(':'):
   n=x[1:]
   if mods[n][0]=='genrule':generate(n);result += [str(W/'gen'/f) for f in config(n)['out']]
   else:result+=files(config(n).get('srcs',[]))
  else:result+=sorted(glob.glob(x))
 return result
def generate(n):
 if n in gen:return
 gen.add(n);d=config(n); ins=files(d.get('srcs',[]));outs=[str(W/'gen'/f) for f in d['out']]
 for o in outs:pathlib.Path(o).parent.mkdir(parents=True,exist_ok=True)
 cmd=d['cmd'].replace('$(out)',' '.join(map(shlex.quote,outs))).replace('$(in)',' '.join(map(shlex.quote,ins))).replace('$(genDir)',str(W/'gen'))
 for tool in d.get('tools',[]):
  if tool in mods:
   _,tm,tp=mods[tool]
   if mods[tool][0].startswith('cc_'):
    td=config(tool)
    for g in td.get('generated_headers',[]):generate(g)
    print('DEFER_TOOL',tool,flush=True)
    return
   path=tp/tm.get('main',tm.get('srcs',[''])[0]);cmd=cmd.replace('$(location '+tool+')','python3 '+shlex.quote(str(path)))
 for f in ins:cmd=cmd.replace('$(location '+os.path.relpath(f,mods[n][2])+')',shlex.quote(f))
 if '$(' in cmd:raise RuntimeError(cmd)
 print('GENERATE',n,flush=True);subprocess.run(cmd,shell=True,check=True,cwd=S,env={**os.environ,"PYTHONPATH":os.pathsep.join(sorted({str(p.parent) for p in S.rglob("*.py")}))})
def visit(n):
 if n in seen:return
 seen.add(n)
 if n not in mods:external.add(n);return
 d=config(n);includes.update(d.get('export_include_dirs',[]));includes.update(d.get('local_include_dirs',[]))
 for key in ('static_libs','whole_static_libs','header_libs','export_header_lib_headers'):
  for dep in d.get(key,[]):visit(dep)
 for g in d.get('generated_headers',[])+d.get('generated_sources',[]):generate(g)
 sources.update(files(d.get('srcs',[])))
 for g in d.get('generated_sources',[]):sources.update(str(W/'gen'/f) for f in config(g)['out'])
visit('libberberis_arm64')
visit('libberberis_jni_arm64')
print('external',sorted(external));print('sources',len(sources))
(W/'manifest.json').write_text(json.dumps({'sources':sorted(sources),'includes':sorted(includes),'external':sorted(external)},indent=2))
