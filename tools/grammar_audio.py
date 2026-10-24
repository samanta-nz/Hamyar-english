#!/usr/bin/env python3
"""Resumable natural audio build. Requires imageio-ffmpeg.
Commands: python tools/grammar_audio.py import <batch>; ... build
Import only after reviewing pause boundaries. Ambiguous groups stay pending.
Use --groups 0,1,... to explicitly accept groups with six detected pauses.
"""
import argparse
import base64
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import wave
import imageio_ffmpeg
from grammar_selectors import repair

ROOT=Path(__file__).resolve().parents[1]/'lessons/grammar-complete'
FF=imageio_ffmpeg.get_ffmpeg_exe()
REG=ROOT/'recordings.json'
def load(path,default=None):return json.loads(path.read_text()) if path.exists() else default
def save(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def run(args,**kwargs):return subprocess.run([FF,'-v','error',*args],check=True,capture_output=True,**kwargs)
def import_batch(name,groups,no_trailing=None,whole=None):
    whole=whole or set()
    assert whole <= groups
    assert not whole & (no_trailing or set())
    no_trailing=no_trailing or set()
    assert no_trailing <= groups
    batch=ROOT/'batches'/name
    plan=load(batch/'plan.json');registry=load(REG,{})
    assert all(0 <= n < len(plan) and len(plan[n]) == 1 for n in whole), 'Whole groups must contain exactly one item'
    audio=ROOT/'audio';audio.mkdir(exist_ok=True)
    report={'batch':name,'accepted':[],'held':[], 'wholeRecordingGroups':sorted(whole), 'noTrailingSilenceGroups':sorted(no_trailing), 'alignmentMethod':'reviewed pause boundaries, not ASR or manual listening'}
    for n,items in enumerate(plan):
        src=batch/f'{n}.wav';flac=src.with_suffix('.flac')
        if not src.exists() and flac.exists():
            run(['-y','-i',str(flac),'-c:a','pcm_s16le',str(src)])
        log=subprocess.run([FF,'-i',str(src),'-af','silencedetect=noise=-35dB:d=0.20','-f','null','-'],capture_output=True,text=True,check=True).stderr
        pauses=[(float(e),float(d)) for e,d in re.findall(r'silence_end: ([\d.]+) \| silence_duration: ([\d.]+)',log)]
        expected=len(items)-1 if n in no_trailing else len(items)
        if n not in groups or (n not in whole and len(pauses)!=expected):
            report['held'].append({'group':n,'pauses':pauses});continue
        # Explicitly reviewed groups may end without a >=200ms trailing pause.
        internal=[] if n in whole else (pauses if n in no_trailing else pauses[:-1])
        cuts=[0.0]+[e-d/2 for e,d in internal]
        assert len(cuts)==len(items)
        for k,item in enumerate(items):
            text=item['ttsText'];key=hashlib.sha256(text.encode()).hexdigest()
            dest=audio/(key+'.weba')
            args=['-y','-ss',str(cuts[k]),'-i',str(src)]
            if k+1<len(cuts):args+=['-t',str(cuts[k+1]-cuts[k])]
            run(args+['-ac','1','-ar','48000','-c:a','libopus','-b:a','48k','-f','webm',str(dest)])
            run(['-i',str(dest),'-f','null','-'])
            assert dest.read_bytes()[:4]==b'\x1aE\xdf\xa3'
            registry[text]={'file':str(dest.relative_to(ROOT)),'voice':item.get('voice_id','voice-00'),'batch':name,'group':n,'start':cuts[k],'end':cuts[k+1] if k+1<len(cuts) else None,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()}
            report['accepted'].append({'id':item['audioId'],'text':text,'recording':key})
    # Retain all source samples losslessly, including groups held for alignment.
    for src in batch.glob('*.wav'):
        flac=src.with_suffix('.flac')
        run(['-y','-i',str(src),'-c:a','flac','-compression_level','8',str(flac)])
        with wave.open(str(src),'rb') as w:
            assert w.getsampwidth()==2
            pcm=w.readframes(w.getnframes())
        assert run(['-i',str(flac),'-f','s16le','-']).stdout==pcm
        src.unlink()
    save(REG,registry);save(batch/'processing-report.json',report)
    print('Imported',len(report['accepted']),'texts; held groups:',len(report['held']))

def build():
    src=ROOT/'source';out=ROOT/'partial';out.mkdir(exist_ok=True)
    shutil.copytree(src/'assets',out/'assets',dirs_exist_ok=True)
    shutil.copy2(src/'index.html',out/'index.html')
    registry=load(REG,{})
    progress={'status':'IN_PROGRESS','complete':0,'total':0,'uniqueRecordings':len(registry),'lessons':[],'selectorIssues':[],'missingLocalLinks':[],'browserPlayback':'NOT_MANUALLY_TESTED'}
    for path in sorted(src.glob('*.audio.json')):
        m=load(path);html=(src/m['targetHtmlFile']).read_text()
        html,m=repair(html,m)
        counts=Counter(i['audioElementId'] for i in m['items'])
        lesson={'file':m['targetHtmlFile'],'complete':[],'pending':[]}
        for item in m['items']:
            assert item['audioSelector']=='#'+item['audioElementId']
            pattern=r'<audio\b(?=[^>]*\bid="'+re.escape(item['audioElementId'])+r'")[^>]*>'
            tags=list(re.finditer(pattern,html))
            if counts[item['audioElementId']]!=1 or len(tags)!=1:
                progress['selectorIssues'].append({'file':lesson['file'],'id':item['audioElementId'],'htmlMatches':len(tags),'manifestMatches':counts[item['audioElementId']]})
                lesson['pending'].append(item['audioId']);continue
            spoken_text=load(ROOT/'tts-overrides.json',{}).get(item['ttsText'],item['ttsText'])
            record=registry.get(spoken_text)
            if not record:
                lesson['pending'].append(item['audioId']);continue
            data=(ROOT/record['file']).read_bytes()
            assert hashlib.sha256(data).hexdigest()==record['sha256']
            uri='data:audio/webm;codecs=opus;base64,'+base64.b64encode(data).decode()
            old=tags[0].group();clean=re.sub(r'\s+src="[^"]*"','',old)
            html=html.replace(old,clean[:-1]+' src="'+uri+'">',1)
            lesson['complete'].append(item['audioId'])
        (out/lesson['file']).write_text(html)
        progress['complete']+=len(lesson['complete']);progress['total']+=len(m['items']);progress['lessons'].append(lesson)
    for path in out.glob('*.html'):
        for link in re.findall(r'(?:href|src)=["\']([^"\']+)',path.read_text()):
            if link.startswith(('data:','#','http:','https:','javascript:')):continue
            local=link.split('#')[0].split('?')[0]
            if local and not (path.parent/local).exists():progress['missingLocalLinks'].append({'page':path.name,'link':link})
    if progress['complete']==progress['total'] and not progress['selectorIssues']:progress['status']='COMPLETE'
    save(ROOT/'progress.json',progress)
    print(f"Embedded {progress['complete']}/{progress['total']}; missing local links: {len(progress['missingLocalLinks'])}")

p=argparse.ArgumentParser();p.add_argument('command',choices=['import','build']);p.add_argument('batch',nargs='?');p.add_argument('--groups',default='');p.add_argument('--no-trailing-groups',default='')
p.add_argument('--whole-groups',default='',help='Singleton groups retained whole, without silence-based splitting')
a=p.parse_args()
if a.command=='import':
    assert a.batch and a.groups,'Batch and explicit --groups acceptance are required'
    import_batch(a.batch,{int(x) for x in a.groups.split(',')},{int(x) for x in a.no_trailing_groups.split(',') if x},{int(x) for x in a.whole_groups.split(',') if x})
else:build()
