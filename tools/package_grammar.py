#!/usr/bin/env python3
"""Validate generated grammar pages and export bucket files outside Git."""
import base64
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1] / 'lessons/grammar-complete'
class Page(HTMLParser):
    def __init__(self):
        super().__init__(); self.audio = {}; self.buttons = []; self.links = []
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'audio':
            assert a['id'] not in self.audio
            self.audio[a['id']] = a.get('src', '')
        if 'data-audio' in a: self.buttons.append(a['data-audio'])
        for key in ('href', 'src'):
            if key in a: self.links.append(a[key])

def main():
    source = ROOT / 'partial'
    progress = json.loads((ROOT / 'progress.json').read_text())
    assert progress['complete'] == progress['total'] == 2006
    assert not progress['selectorIssues'] and not progress['missingLocalLinks']
    registry = json.loads((ROOT / 'recordings.json').read_text())
    hashes = {r['sha256'] for r in registry.values()}
    browser = json.loads((ROOT / 'browser-validation.json').read_text())
    assert browser['status'] == 'PASS'
    report = {'embedded': 0, 'pages': [], 'browserPlayback': browser}
    for path in sorted(source.glob('*.html')):
        p = Page(); p.feed(path.read_text()); embedded = 0
        for uri in p.audio.values():
            assert uri.startswith('data:audio/webm;codecs=opus;base64,'), path
            data = base64.b64decode(uri.split(',', 1)[1], validate=True)
            assert hashlib.sha256(data).hexdigest() in hashes
            embedded += 1
        for target in p.buttons:
            assert p.audio.get('audio_' + target), (path, target)
        for link in p.links:
            if link.startswith(('data:', '#', 'http:', 'https:', 'javascript:')): continue
            local = link.split('#')[0].split('?')[0]
            assert not local or (source / local).exists(), (path, link)
        report['embedded'] += embedded
        report['pages'].append({'file': path.name, 'audio': embedded, 'buttons': len(p.buttons)})
    assert report['embedded'] == 2006
    dest = Path(sys.argv[1]).resolve()
    assert ROOT not in dest.parents and dest != ROOT
    dest.mkdir(parents=True, exist_ok=True)
    bucket = dest / 'html'
    shutil.copytree(source, bucket, dirs_exist_ok=True)
    (dest / 'validation.json').write_text(json.dumps(report, indent=2) + '\n')
    archive = dest / 'english-grammar-complete-bucket.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in sorted(bucket.rglob('*')):
            if file.is_file(): z.write(file, file.relative_to(bucket))
    with zipfile.ZipFile(archive) as z: assert z.testzip() is None
    print(json.dumps(report, indent=2)); print(archive)

if __name__ == '__main__': main()
