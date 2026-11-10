#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Fetches all 100 TeaTime Chinese episodes and official SRT subtitles from teatimechinese.com,
parses the exact sentence timestamps, extracts CC-CEDICT vocabulary, calculates target HSK levels,
and bundles them into app/src/main/assets/teatime_transcripts.json for offline synchronization.
"""

import os
import re
import json
import sqlite3
import zipfile
import urllib.request
from datetime import datetime, timezone

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
ZIP_PATH = os.path.join(PROJECT_ROOT, "app/src/main/res/raw/cedict_db.zip")
OUTPUT_JSON_PATH = os.path.join(PROJECT_ROOT, "app/src/main/assets/teatime_transcripts.json")
EXTRACTED_DB = "/tmp/cedict_db"

def parse_time(t_str):
    parts = re.split(r'[:,\.]', t_str.strip())
    h, m, s, ms = int(parts[0]), int(parts[1]), int(parts[2]), int(parts[3])
    return (h * 3600 + m * 60 + s) * 1000 + ms

def parse_duration_to_ms(duration_str):
    parts = duration_str.strip().split(':')
    if len(parts) == 2:
        return (int(parts[0]) * 60 + int(parts[1])) * 1000
    elif len(parts) == 3:
        return (int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])) * 1000
    return 0

def parse_date_to_ms(date_str):
    try:
        dt = datetime.strptime(date_str.strip(), "%Y-%m-%d").replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except Exception:
        return 0

def parse_srt(content):
    cues = []
    blocks = re.split(r'\n\s*\n', content.replace('\r\n', '\n').strip())
    cue_idx = 1
    for b in blocks:
        lines = [l.strip() for l in b.split('\n') if l.strip()]
        if not lines:
            continue
        time_line_idx = -1
        for i, l in enumerate(lines):
            if '-->' in l:
                time_line_idx = i
                break
        if time_line_idx == -1:
            continue
        time_part = lines[time_line_idx]
        start_str, end_str = time_part.split('-->')
        start_ms = parse_time(start_str)
        end_ms = parse_time(end_str)
        text_lines = [l for l in lines[time_line_idx+1:] if l != '[BREAK]']
        text = ' '.join(text_lines).strip()
        if text:
            cues.append({
                'idx': cue_idx,
                's': start_ms,
                'e': end_ms,
                't': text
            })
            cue_idx += 1
    return cues

def fetch_catalog_episodes():
    catalog_cache = '/tmp/teatime_catalog_payload.js'
    if not os.path.exists(catalog_cache):
        print("Fetching catalog from https://teatimechinese.com/podcast/chinese...")
        req = urllib.request.Request(
            'https://teatimechinese.com/podcast/chinese',
            headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36'}
        )
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
        for s in scripts:
            if 'subtitlesUrl' in s:
                with open(catalog_cache, 'w') as f:
                    f.write(s)
                break

    with open(catalog_cache, 'r') as f:
        data = json.load(f)

    episodes = []
    for item in data:
        if isinstance(item, dict) and 'slug' in item and 'audioUrl' in item:
            resolved = {}
            for k, v in item.items():
                if isinstance(v, int) and v < len(data):
                    resolved[k] = data[v]
                else:
                    resolved[k] = v
            episodes.append(resolved)
    return episodes

def main():
    if not os.path.exists(EXTRACTED_DB):
        print("Extracting CC-CEDICT database...")
        with zipfile.ZipFile(ZIP_PATH, 'r') as zf:
            zf.extract('cedict_db', '/tmp')

    conn = sqlite3.connect(EXTRACTED_DB)
    cursor = conn.cursor()

    print("Loading CC-CEDICT word mappings and translations...")
    cursor.execute('SELECT chinese, pinyin, cword_id FROM cword')
    cword_map = {}
    for ch, py, cid in cursor.fetchall():
        if ch not in cword_map:
            cword_map[ch] = (py, cid)

    cursor.execute('SELECT cword_id, translation FROM eword')
    trans_map = {}
    for cid, tr in cursor.fetchall():
        if cid not in trans_map:
            trans_map[cid] = []
        if len(trans_map[cid]) < 3:
            trans_map[cid].append(tr)

    cursor.execute('SELECT hanzi, hsk_level FROM hsk_vocab')
    hsk_map = {hz: lv for hz, lv in cursor.fetchall()}

    raw_episodes = fetch_catalog_episodes()
    print(f"Found {len(raw_episodes)} raw episodes in catalog payload.")

    processed = []
    total_vocab = 0
    total_sentences = 0

    for idx, ep in enumerate(raw_episodes):
        slug = ep.get('slug', '')
        title = ep.get('title', '')
        ep_num_match = re.search(r'第\s*(\d+)\s*集', title) or re.search(r'^\s*(\d+)', slug)
        ep_num = int(ep_num_match.group(1)) if ep_num_match else (idx + 1)

        duration_ms = parse_duration_to_ms(ep.get('duration', '0:0'))
        pub_date_ms = parse_date_to_ms(ep.get('date', ''))
        audio_url = ep.get('audioUrl', '')
        srt_url = ep.get('subtitlesUrl', '')

        # Standardize SRT URL padding if needed
        if not srt_url or 'TTCN+' not in srt_url:
            pad = f"{ep_num:02d}" if ep_num < 10 else f"{ep_num}"
            srt_url = f"https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+{pad}/TTCN+{pad}.srt"

        print(f"[{idx+1}/{len(raw_episodes)}] Ep {ep_num}: {title[:35]}... (fetching SRT)")
        cues = []
        os.makedirs('/tmp/teatime_srts', exist_ok=True)
        cached_srt = f"/tmp/teatime_srts/TTCN_{ep_num:02d}.srt"
        srt_content = None
        if os.path.exists(cached_srt):
            with open(cached_srt, 'r', encoding='utf-8', errors='ignore') as f:
                srt_content = f.read()
        else:
            try:
                req = urllib.request.Request(srt_url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req, timeout=15) as resp:
                    srt_content = resp.read().decode('utf-8', errors='ignore')
                with open(cached_srt, 'w', encoding='utf-8') as f:
                    f.write(srt_content)
            except Exception as e:
                print(f"  Warning: Failed to fetch {srt_url}: {e}")

        if srt_content:
            cues = parse_srt(srt_content)

        # Build full transcript from cue texts
        transcript = '\n'.join([c['t'] for c in cues])
        total_sentences += len(cues)

        # CC-CEDICT vocabulary segmentation
        blocks = [b for b in re.split(r'[^\u4E00-\u9FA5]+', transcript) if len(b) >= 2]
        candidates = set()
        for block in blocks:
            for i in range(len(block)):
                for l in range(2, min(5, len(block) - i + 1)):
                    candidates.add(block[i:i+l])

        matched = {c for c in candidates if c in cword_map}
        ordered = []
        seen = set()
        for block in blocks:
            i = 0
            while i < len(block):
                matched_len = 0
                for l in range(min(4, len(block) - i), 1, -1):
                    cand = block[i:i+l]
                    if cand in matched:
                        matched_len = l
                        if cand not in seen:
                            seen.add(cand)
                            ordered.append(cand)
                        break
                if matched_len > 0:
                    i += matched_len
                else:
                    i += 1

        vocab_items = []
        valid_levels = []
        for ch in ordered:
            py, cid = cword_map[ch]
            tr = '; '.join(trans_map.get(cid, []))
            raw_hsk = hsk_map.get(ch, 0)
            hsk = raw_hsk if 1 <= raw_hsk <= 6 else 0
            if 1 <= hsk <= 6:
                valid_levels.append(hsk)
            vocab_items.append({
                'c': ch,
                'p': py,
                't': tr,
                'h': hsk
            })

        valid_levels.sort()
        if valid_levels:
            p80_idx = min(len(valid_levels) - 1, int(len(valid_levels) * 0.8))
            target_hsk = valid_levels[p80_idx]
        else:
            target_hsk = 3

        total_vocab += len(vocab_items)

        processed.append({
            'episodeNum': ep_num,
            'title': title,
            'slug': slug,
            'audioUrl': audio_url,
            'subtitlesUrl': srt_url,
            'durationMs': duration_ms,
            'pubDate': pub_date_ms,
            'transcript': transcript,
            'sentences': cues,
            'targetHskLevel': target_hsk,
            'vocab': vocab_items
        })

    # Sort episodes ascending by episodeNum
    processed.sort(key=lambda x: x['episodeNum'])

    print(f"\nWriting {len(processed)} episodes ({total_sentences} sentences, {total_vocab} vocab words) to {OUTPUT_JSON_PATH}...")
    with open(OUTPUT_JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(processed, f, ensure_ascii=False)

    file_size_mb = os.path.getsize(OUTPUT_JSON_PATH) / 1024 / 1024
    print(f"Successfully generated {OUTPUT_JSON_PATH} ({file_size_mb:.2f} MB)")

if __name__ == '__main__':
    main()
