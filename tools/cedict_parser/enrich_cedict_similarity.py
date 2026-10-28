#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Enrich CC-CEDICT SQLite database with precomputed character visual similarity scores
using MakeMeAHanzi decomposition, etymology, stroke counts, and font bitmap IoU.
Updates cedict_db to version 9 and re-compresses app/src/main/res/raw/cedict_db.zip.
"""

import json
import os
import shutil
import sqlite3
import tempfile
import time
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

IDC_SET = set("⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻")
ROOM_IDENTITY_HASH = "27a47e93ae1d280a71096c5fdf061e16"

def find_font():
    font_paths = [
        "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
    ]
    for p in font_paths:
        if os.path.exists(p):
            return p
    return None

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    raw_res_dir = os.path.join(root_dir, "app/src/main/res/raw")
    existing_zip = os.path.join(raw_res_dir, "cedict_db.zip")
    output_zip = os.path.join(raw_res_dir, "cedict_db.zip")
    makeme_path = os.path.join(root_dir, "assets/makemeahanzi/dictionary.txt")

    if not os.path.exists(existing_zip):
        raise FileNotFoundError(f"Source database zip not found at {existing_zip}")
    if not os.path.exists(makeme_path):
        raise FileNotFoundError(f"MakeMeAHanzi data not found at {makeme_path}")

    font_path = find_font()
    print(f"Using font for bitmap comparison: {font_path}")

    def render_bitmap(char, size=64):
        if not font_path:
            return None
        img = Image.new("L", (size, size), color=0)
        draw = ImageDraw.Draw(img)
        font = ImageFont.truetype(font_path, int(size * 0.8))
        bbox = draw.textbbox((0, 0), char, font=font)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text(((size - w) / 2, (size - h) / 2 - bbox[1]), char, font=font, fill=255)
        return np.array(img, dtype=np.float32).flatten() / 255.0

    t0 = time.time()
    print("Loading MakeMeAHanzi dictionary...")
    characters = {}
    comp_to_chars = {}

    with open(makeme_path, "r", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            c = d.get("character")
            if not c:
                continue
            decomp = d.get("decomposition", "")
            layout = decomp[0] if decomp and decomp[0] in IDC_SET else None
            components = [ch for ch in decomp if ch not in IDC_SET and ch != "？"]
            matches = d.get("matches", [])
            stroke_count = len(matches) if matches else 1
            radical = d.get("radical")

            comp_strokes = {}
            for m in matches:
                if m and len(m) > 0 and m[0] < len(components):
                    comp_name = components[m[0]]
                    comp_strokes[comp_name] = comp_strokes.get(comp_name, 0) + 1

            for comp in components:
                if comp not in comp_to_chars:
                    comp_to_chars[comp] = []
                comp_to_chars[comp].append(c)

            characters[c] = {
                "layout": layout,
                "components": components,
                "comp_strokes": comp_strokes,
                "stroke_count": stroke_count,
                "radical": radical
            }

    print(f"Loaded {len(characters)} characters from MakeMeAHanzi in {time.time() - t0:.2f}s")

    # Pre-render bitmaps for simple characters (stroke_count <= 5) for bitmap IoU
    print("Pre-rendering bitmaps for simple/small stroke characters...")
    simple_chars = [c for c, d in characters.items() if d["stroke_count"] <= 5]
    simple_bitmaps = {}
    if font_path:
        for c in simple_chars:
            b = render_bitmap(c)
            if b is not None:
                simple_bitmaps[c] = b

    print(f"Pre-rendered {len(simple_bitmaps)} simple character bitmaps.")

    # Compute high-similarity simple character pairs (Bitmap IoU)
    simple_pairs = {}
    simple_list = list(simple_bitmaps.keys())
    for i in range(len(simple_list)):
        c1 = simple_list[i]
        b1 = simple_bitmaps[c1]
        for j in range(i + 1, len(simple_list)):
            c2 = simple_list[j]
            # Must have close stroke count (within +-1)
            if abs(characters[c1]["stroke_count"] - characters[c2]["stroke_count"]) > 1:
                continue
            b2 = simple_bitmaps[c2]
            inter = np.sum(np.minimum(b1, b2))
            union = np.sum(np.maximum(b1, b2))
            iou = float(inter / union) if union > 0 else 0.0
            if iou >= 0.50:
                score = round(min(0.99, iou), 3)
                simple_pairs.setdefault(c1, []).append((c2, score, None))
                simple_pairs.setdefault(c2, []).append((c1, score, None))

    print(f"Computed simple bitmap pairs for {len(simple_pairs)} characters.")

    with tempfile.TemporaryDirectory() as tmpdir:
        extracted_db = os.path.join(tmpdir, "cedict_db")
        print(f"Extracting {existing_zip}...")
        with zipfile.ZipFile(existing_zip, "r") as z:
            name = z.namelist()[0]
            with open(extracted_db, "wb") as f:
                f.write(z.read(name))

        conn = sqlite3.connect(extracted_db)
        cursor = conn.cursor()

        cursor.execute("SELECT DISTINCT hanzi FROM char_pinyin")
        cedict_hanzi = [row[0] for row in cursor.fetchall()]
        print(f"Loaded {len(cedict_hanzi)} distinct hanzi from CEDICT database.")

        similarity_rows = []
        total_pairs_count = 0

        for char in cedict_hanzi:
            scored_candidates = {}

            # 1. Check simple character bitmap matches
            if char in simple_pairs:
                for sim, score, comp in simple_pairs[char]:
                    scored_candidates[sim] = (score, comp)

            # 2. Check component decomposition matches
            q = characters.get(char)
            if q:
                candidates = set()
                for comp in q["components"]:
                    candidates.update(comp_to_chars.get(comp, []))
                candidates.discard(char)

                for cand in candidates:
                    c_data = characters[cand]
                    shared = set(q["components"]) & set(c_data["components"])
                    if not shared:
                        continue
                    shared_strokes = sum(min(q["comp_strokes"].get(s, 0), c_data["comp_strokes"].get(s, 0)) for s in shared)
                    max_strokes = max(q["stroke_count"], c_data["stroke_count"])
                    if max_strokes == 0:
                        continue
                    score = shared_strokes / max_strokes

                    # Layout structure bonus (e.g. both left-right)
                    if q["layout"] and q["layout"] == c_data["layout"]:
                        score += 0.05

                    if score >= 0.30:
                        primary_shared = max(shared, key=lambda s: q["comp_strokes"].get(s, 0))
                        final_score = round(min(0.99, score), 3)
                        # Keep max score if already present
                        prev = scored_candidates.get(cand)
                        if prev is None or final_score > prev[0]:
                            scored_candidates[cand] = (final_score, primary_shared)

            # Sort and keep top 12 candidates
            if scored_candidates:
                sorted_cands = sorted(scored_candidates.items(), key=lambda x: x[1][0], reverse=True)[:12]
                for sim_char, (score, comp) in sorted_cands:
                    similarity_rows.append((char, sim_char, score, comp))
                    total_pairs_count += 1

        print(f"Generated {total_pairs_count} similarity pairs for {len(cedict_hanzi)} characters.")

        # Create table & index
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS `char_similarity` (
                `hanzi` TEXT NOT NULL,
                `similar_hanzi` TEXT NOT NULL,
                `score` REAL NOT NULL,
                `shared_component` TEXT,
                PRIMARY KEY(`hanzi`, `similar_hanzi`)
            );
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS `index_char_similarity_hanzi_score` ON `char_similarity` (`hanzi`, `score`);")

        print("Inserting similarity rows into database...")
        cursor.executemany(
            "INSERT OR REPLACE INTO `char_similarity` (`hanzi`, `similar_hanzi`, `score`, `shared_component`) VALUES (?, ?, ?, ?)",
            similarity_rows
        )

        # Set user_version to 9 and update Room master table
        cursor.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
        cursor.execute(f"INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '{ROOM_IDENTITY_HASH}');")
        cursor.execute("PRAGMA user_version = 9;")

        conn.commit()
        cursor.execute("VACUUM;")
        conn.close()

        print(f"Compressing updated database into {output_zip}...")
        with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            z.write(extracted_db, arcname="cedict_db")

        out_size = os.path.getsize(output_zip)
        print(f"Done! Successfully created {output_zip} ({out_size:,} bytes).")

if __name__ == "__main__":
    main()

