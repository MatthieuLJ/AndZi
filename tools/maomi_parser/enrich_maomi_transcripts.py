#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Enriches maomi_transcripts.json with pre-segmented vocabulary and target HSK levels
from CC-CEDICT (cword, eword, hsk_vocab), enabling instant offline vocabulary and
mastery tracking on Android without expensive runtime dictionary queries.
"""

import zipfile
import sqlite3
import json
import re
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
ZIP_PATH = os.path.join(PROJECT_ROOT, "app/src/main/res/raw/cedict_db.zip")
JSON_PATH = os.path.join(PROJECT_ROOT, "app/src/main/assets/maomi_transcripts.json")
EXTRACTED_DB = "/tmp/cedict_db"

def main():
    print("Extracting CC-CEDICT database...")
    with zipfile.ZipFile(ZIP_PATH, 'r') as zf:
        zf.extract('cedict_db', '/tmp')

    conn = sqlite3.connect(EXTRACTED_DB)
    cursor = conn.cursor()

    print("Fetching CC-CEDICT word mappings and translations...")
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

    print(f"Reading transcripts from {JSON_PATH}...")
    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        maomi = json.load(f)

    enriched = []
    total_vocab_count = 0
    for ep in maomi:
        t = ep['transcript']
        blocks = [b for b in re.split(r'[^\u4E00-\u9FA5]+', t) if len(b) >= 2]
        
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
            
        ep_copy = dict(ep)
        ep_copy['targetHskLevel'] = target_hsk
        ep_copy['vocab'] = vocab_items
        enriched.append(ep_copy)
        total_vocab_count += len(vocab_items)

    print(f"Writing {len(enriched)} enriched episodes ({total_vocab_count} vocab entries) to {JSON_PATH}...")
    with open(JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(enriched, f, ensure_ascii=False)

    print(f"Successfully enriched! Size: {os.path.getsize(JSON_PATH) / 1024 / 1024:.2f} MB")

if __name__ == '__main__':
    main()
