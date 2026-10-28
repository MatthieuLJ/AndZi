#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Enrich CC-CEDICT SQLite database with curated HSK 3.0, HSK 2.0, and Jun Da / corpus frequency rankings.
Produces the pre-packaged zip database for Android: app/src/main/res/raw/cedict_db.zip
"""

import csv
import glob
import os
import shutil
import sqlite3
import tempfile
import zipfile

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    raw_res_dir = os.path.join(root_dir, "app/src/main/res/raw")
    existing_png_zip = os.path.join(raw_res_dir, "cedict_db.png")
    existing_zip = os.path.join(raw_res_dir, "cedict_db.zip")
    output_zip = os.path.join(raw_res_dir, "cedict_db.zip")

    with tempfile.TemporaryDirectory() as tmpdir:
        extracted_db = os.path.join(tmpdir, "cedict_db")

        # Source zip file
        src_zip = existing_zip if os.path.exists(existing_zip) else existing_png_zip
        print(f"Reading existing dictionary from {src_zip}...")
        with zipfile.ZipFile(src_zip, 'r') as z:
            name = z.namelist()[0]
            with open(extracted_db, 'wb') as f:
                f.write(z.read(name))

        conn = sqlite3.connect(extracted_db)
        cursor = conn.cursor()

        # Ensure base CEDICT tables are indexed
        cursor.execute("SELECT chinese FROM cword")
        cedict_words = set(row[0] for row in cursor.fetchall())
        print(f"Loaded {len(cedict_words)} distinct words from CEDICT.")

        # 1. Load SUBTLEX-CH Character and Word Frequency Ranks (Ghent University / PLoS ONE, CC BY)
        subtlex_char_ranks = {}
        subtlex_chr_path = os.path.join(root_dir, "assets/hskhsk.com/cidian/data/SUBTLEX-CH-CHR.txt")
        if os.path.exists(subtlex_chr_path):
            with open(subtlex_chr_path, 'r', encoding='utf-8') as f:
                rank = 1
                for line in f:
                    parts = line.strip().split("\t")
                    if len(parts) >= 2 and parts[1].isdigit():
                        ch = parts[0].strip('\ufeff')
                        subtlex_char_ranks[ch] = rank
                        rank += 1
            print(f"Loaded {len(subtlex_char_ranks)} character frequency ranks from SUBTLEX-CH.")

        subtlex_word_ranks = {}
        subtlex_wf_path = os.path.join(root_dir, "assets/hskhsk.com/cidian/data/SUBTLEX-CH-WF.txt")
        if os.path.exists(subtlex_wf_path):
            with open(subtlex_wf_path, 'r', encoding='utf-8') as f:
                rank = 1
                for line in f:
                    parts = line.strip().split("\t")
                    if len(parts) >= 2 and parts[1].isdigit():
                        w = parts[0].strip('\ufeff')
                        subtlex_word_ranks[w] = rank
                        rank += 1
            print(f"Loaded {len(subtlex_word_ranks)} word frequency ranks from SUBTLEX-CH.")

        # 2. Load HSK 3.0 characters from assets/hsk30/hsk30-chars.csv (MIT License)
        hsk_vocab = {}
        hsk3_chars_csv = os.path.join(root_dir, "assets/hsk30/hsk30-chars.csv")
        if os.path.exists(hsk3_chars_csv):
            with open(hsk3_chars_csv, 'r', encoding='utf-8') as f:
                for idx, r in enumerate(csv.DictReader(f)):
                    hz = r['Hanzi'].strip()
                    if hz in cedict_words:
                        lvl_str = r['Level'].strip()
                        lvl = 7 if lvl_str in ('7', '7-9') else (int(lvl_str) if lvl_str.isdigit() else 1)
                        freq_rank = subtlex_char_ranks.get(hz, (lvl * 1000) + idx)
                        hsk_vocab[hz] = {
                            'hsk_level': lvl,
                            'hsk2_level': 0,
                            'freq_rank': freq_rank,
                            'is_char': 1
                        }
            print(f"Loaded HSK 3.0 characters from assets/hsk30 (current entries: {len(hsk_vocab)}).")

        # 3. Load HSK 3.0 words from assets/hsk30/hsk30.csv (MIT License)
        hsk3_words_csv = os.path.join(root_dir, "assets/hsk30/hsk30.csv")
        if os.path.exists(hsk3_words_csv):
            with open(hsk3_words_csv, 'r', encoding='utf-8') as f:
                for idx, r in enumerate(csv.DictReader(f)):
                    raw_words = r['Simplified'].strip()
                    lvl_str = r['Level'].strip()
                    lvl = 7 if lvl_str in ('7', '7-9') else (int(lvl_str) if lvl_str.isdigit() else 1)
                    for w in raw_words.split('|'):
                        w = w.strip()
                        if w and w in cedict_words:
                            w_rank = subtlex_word_ranks.get(w, (lvl * 1000) + idx)
                            if w in hsk_vocab:
                                hsk_vocab[w]['hsk_level'] = min(hsk_vocab[w]['hsk_level'], lvl)
                                hsk_vocab[w]['freq_rank'] = min(hsk_vocab[w]['freq_rank'], w_rank)
                            else:
                                hsk_vocab[w] = {
                                    'hsk_level': lvl,
                                    'hsk2_level': 0,
                                    'freq_rank': w_rank,
                                    'is_char': 1 if len(w) == 1 else 0
                                }
            print(f"Loaded HSK 3.0 words from assets/hsk30 (current entries: {len(hsk_vocab)}).")

        # 4. Load HSK 2012 frequency order lists (MIT License)
        hsk2_lists = sorted(glob.glob(os.path.join(root_dir, "assets/hskhsk.com/data/lists/HSK Official With Definitions 2012 L*freqorder.txt")))
        for path in hsk2_lists:
            lvl_str = os.path.basename(path).split("L")[-1].split(" ")[0]
            lvl2 = int(lvl_str) if lvl_str.isdigit() else 1
            with open(path, 'r', encoding='utf-8') as f:
                for idx, line in enumerate(f):
                    parts = line.strip().split("\t")
                    if parts and parts[0]:
                        w = parts[0].strip('\ufeff')
                        if w in cedict_words:
                            w_rank = subtlex_word_ranks.get(w, idx + 1)
                            if w in hsk_vocab:
                                hsk_vocab[w]['hsk2_level'] = lvl2
                                hsk_vocab[w]['freq_rank'] = min(hsk_vocab[w]['freq_rank'], w_rank)
                            else:
                                hsk_vocab[w] = {
                                    'hsk_level': lvl2,
                                    'hsk2_level': lvl2,
                                    'freq_rank': w_rank,
                                    'is_char': 1 if len(w) == 1 else 0
                                }
        print(f"Integrated HSK 2.0 lists (current entries: {len(hsk_vocab)}).")

        # 5. Enrich with high-frequency spoken words from SUBTLEX-CH (top 10,000 words in CEDICT)
        print("Enriching dictionary words with top SUBTLEX-CH spoken frequencies...")
        enriched_count = 0
        for w, rank in subtlex_word_ranks.items():
            if rank <= 10000 and w in cedict_words:
                if w in hsk_vocab:
                    hsk_vocab[w]['freq_rank'] = min(hsk_vocab[w]['freq_rank'], rank)
                else:
                    hsk_vocab[w] = {
                        'hsk_level': 99,
                        'hsk2_level': 0,
                        'freq_rank': rank,
                        'is_char': 1 if len(w) == 1 else 0
                    }
                    enriched_count += 1

        print(f"Enriched {enriched_count} additional high-frequency words. Total curated vocabulary: {len(hsk_vocab)}")

        # Create table in SQLite
        cursor.execute("DROP TABLE IF EXISTS hsk_vocab;")
        cursor.execute("""
            CREATE TABLE hsk_vocab (
                hanzi TEXT PRIMARY KEY NOT NULL,
                hsk_level INTEGER NOT NULL,
                hsk2_level INTEGER NOT NULL DEFAULT 0,
                freq_rank INTEGER NOT NULL DEFAULT 999999,
                is_char INTEGER NOT NULL DEFAULT 0
            );
        """)
        cursor.execute("CREATE INDEX idx_hsk_level ON hsk_vocab(hsk_level);")
        cursor.execute("CREATE INDEX idx_hsk_freq ON hsk_vocab(freq_rank);")

        # Insert entries
        rows_to_insert = [
            (w, v['hsk_level'], v['hsk2_level'], v['freq_rank'], v['is_char'])
            for w, v in hsk_vocab.items()
        ]
        cursor.executemany("INSERT INTO hsk_vocab (hanzi, hsk_level, hsk2_level, freq_rank, is_char) VALUES (?,?,?,?,?)", rows_to_insert)

        # Ensure leitner table exists for Room
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS `leitner` (
                `hanzi` TEXT NOT NULL,
                `box` INTEGER NOT NULL,
                `exp_time` INTEGER NOT NULL,
                `num_tested` INTEGER NOT NULL DEFAULT 0,
                `num_wrong` INTEGER NOT NULL DEFAULT 0,
                `streak` INTEGER NOT NULL DEFAULT 0,
                `date_added` INTEGER NOT NULL,
                PRIMARY KEY(`hanzi`)
            );
        """)

        cursor.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
        cursor.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '27a47e93ae1d280a71096c5fdf061e16');")
        cursor.execute("PRAGMA user_version = 9;")

        conn.commit()
        cursor.execute("VACUUM;")
        conn.close()

        # Compress database into cedict_db.zip
        print(f"Compressing into {output_zip}...")
        with zipfile.ZipFile(output_zip, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            z.write(extracted_db, arcname="cedict_db")

        print(f"Done! Created {output_zip} (size: {os.path.getsize(output_zip):,} bytes)")

        # Clean up legacy .png if it existed
        if os.path.exists(existing_png_zip):
            os.remove(existing_png_zip)
            print(f"Removed legacy {existing_png_zip}")

if __name__ == "__main__":
    main()
