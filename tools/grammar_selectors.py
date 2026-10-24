"""Repair known duplicate audio selectors using unique semantic text anchors.
Only generated HTML and in-memory manifest items are changed.
"""
from collections import Counter
import copy
import html as markup
import re


def repair(html, manifest):
    manifest = copy.deepcopy(manifest)
    counts = Counter(x['audioElementId'] for x in manifest['items'])
    for old, count in counts.items():
        if count == 1:
            continue
        items = [x for x in manifest['items'] if x['audioElementId'] == old]
        pattern = r'<audio\b[^>]*\bid="' + re.escape(old) + r'"[^>]*></audio>'
        assert len(re.findall(pattern, html)) == count
        # Empty bank entries have no content association: remove them and create
        # distinct entries bound to uniquely matched visible semantic anchors.
        html = re.sub(pattern, '', html)
        for item in items:
            assert item['kind'] in ('example', 'listening')
            new = old + '_' + item['kind']
            assert f'id="{new}"' not in html
            audio_id = new.removeprefix('audio_')
            button = f'<button type="button" class="speak" data-audio="{audio_id}" aria-label="Play audio">▶</button>'
            text = re.escape(markup.escape(item['ttsText'], quote=False))
            if item['kind'] == 'example':
                anchor = r'(<span\b[^>]*class="example-en"[^>]*>' + text + r'</span>)'
                assert len(re.findall(anchor, html)) >= 1, item['ttsText']
                html = re.sub(anchor, lambda m: m[0] + button, html)
            else:
                anchor = (r'(<div\b[^>]*class="listen-card"[^>]*data-tts-text="'
                          + re.escape(markup.escape(item['ttsText'], quote=True))
                          + r'"[^>]*>\s*<div class="listen-head">)'
                            r'<span class="audio-pending"[^>]*>.*?</span>')
                assert len(re.findall(anchor, html)) == 1, item['ttsText']
                html = re.sub(anchor, lambda m: m[1] + button, html)
            html = html.replace('</body>', f'<audio id="{new}" preload="none" hidden></audio></body>')
            item['audioElementId'] = new
            item['audioSelector'] = '#' + new
            item['audioId'] = audio_id
    return html, manifest
