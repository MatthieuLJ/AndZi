#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Enrich CC-CEDICT SQLite database with precomputed character visual similarity scores
using MakeMeAHanzi decomposition, stem matching, stroke counts, and font bitmap IoU.
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

# Common semantic classifier radicals: sharing ONLY one of these is a semantic category,
# not a distinctive visual stem twin (e.g. 汤 vs 江/池).
COMMON_CLASSIFIER_RADICALS = set("氵冫亻彳纟讠钅饣木火土艹口犭阝女日月目禾疒小")

# Known MakeMeAHanzi decomposition gaps / unnamed glyph fixes
STEM_FIXES = {
    "经": ["纟", "圣"],
    "轻": ["车", "圣"],
    "茎": ["艹", "圣"],
    "径": ["彳", "圣"],
    "烃": ["火", "圣"],
    "胫": ["月", "圣"],
    "泾": ["氵", "圣"],
    "颈": ["圣", "页"],
    "场": ["土", "昜"],
    "乐": ["丿", "ㄥ", "小"],
    "东": ["七", "小"],
    "车": ["七", "十"],
    "乌": ["丿", "马"],
    "鸟": ["丶", "乌", "马"],
}

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
            components = STEM_FIXES.get(c) or [ch for ch in decomp if ch not in IDC_SET and ch != "？"]
            matches = d.get("matches", [])
            stroke_count = len(matches) if matches else 1
            radical = d.get("radical")

            comp_strokes = {}
            for m in matches:
                if m and len(m) > 0 and m[0] < len(components):
                    comp_name = components[m[0]]
                    comp_strokes[comp_name] = comp_strokes.get(comp_name, 0) + 1

            if c == "经": comp_strokes = {"纟": 3, "圣": 5}
            if c == "轻": comp_strokes = {"车": 4, "圣": 5}
            if c == "场": comp_strokes = {"土": 3, "昜": 3}
            if c == "乌": comp_strokes = {"丿": 1, "马": 3}
            if c == "鸟": comp_strokes = {"丶": 1, "乌": 4, "马": 3}

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
        cedict_hanzi = set(row[0] for row in cursor.fetchall())
        print(f"Loaded {len(cedict_hanzi)} distinct hanzi from CEDICT database.")

        # Pre-render bitmaps for font comparison
        font_obj = ImageFont.truetype(font_path, int(64 * 0.8)) if font_path else None
        bitmaps = {}

        def get_bitmap(ch):
            if ch not in bitmaps and font_obj:
                img = Image.new("L", (64, 64), color=0)
                draw = ImageDraw.Draw(img)
                bbox = draw.textbbox((0, 0), ch, font=font_obj)
                w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
                draw.text(((64 - w) / 2, (64 - h) / 2 - bbox[1]), ch, font=font_obj, fill=255)
                bitmaps[ch] = (np.array(img, dtype=np.float32).flatten() > 30).astype(np.float32)
            return bitmaps.get(ch)

        def calc_iou(c1, c2):
            b1 = get_bitmap(c1)
            b2 = get_bitmap(c2)
            if b1 is None or b2 is None:
                return 0.0
            inter = np.dot(b1, b2)
            union = b1.sum() + b2.sum() - inter
            return float(inter / union) if union > 0 else 0.0

        # Group cedict characters by stroke count
        by_strokes = {}
        for c in cedict_hanzi:
            if c in characters:
                s = characters[c]["stroke_count"]
                by_strokes.setdefault(s, []).append(c)

        print(f"Pre-rendering and computing stroke-bucketed bitmap IoU...")
        t_bm = time.time()
        for c in cedict_hanzi:
            get_bitmap(c)

        bitmap_pairs = {}
        for s, s_chars in by_strokes.items():
            cand_chars = list(s_chars)
            if s + 1 in by_strokes:
                cand_chars.extend(by_strokes[s + 1])
            if s <= 5 and s + 2 in by_strokes:
                cand_chars.extend(by_strokes[s + 2])

            mat1 = np.array([bitmaps[c] for c in s_chars if c in bitmaps])
            valid_cands = [c for c in cand_chars if c in bitmaps]
            mat2 = np.array([bitmaps[c] for c in valid_cands])

            if len(mat1) == 0 or len(mat2) == 0:
                continue

            inter = np.dot(mat1, mat2.T)
            sums1 = mat1.sum(axis=1)[:, None]
            sums2 = mat2.sum(axis=1)[None, :]
            union = sums1 + sums2 - inter
            iou = inter / np.maximum(union, 1e-5)

            rows, cols = np.where(iou >= 0.34)
            for r, col in zip(rows, cols):
                c1 = s_chars[r]
                c2 = valid_cands[col]
                if c1 != c2:
                    val = round(float(iou[r, col]), 3)
                    bitmap_pairs.setdefault(c1, {})[c2] = val
                    bitmap_pairs.setdefault(c2, {})[c1] = val

        print(f"Computed bitmap IoU for {len(bitmap_pairs)} characters in {time.time() - t_bm:.2f}s.")

        direct_scores = {}

        for char in cedict_hanzi:
            scored_candidates = {}

            # 1. Component & Stem Matching
            q = characters.get(char)
            if q:
                candidates = set()
                for comp in q["components"]:
                    candidates.update(comp_to_chars.get(comp, []))
                candidates.update(comp_to_chars.get(char, []))
                candidates.update(q["components"])
                candidates.discard(char)
                candidates = [c for c in candidates if c in cedict_hanzi and c in characters]

                for cand in candidates:
                    c_data = characters[cand]
                    shared = set(q["components"]) & set(c_data["components"])
                    if char in c_data["components"]:
                        # If char is primitive (<= 4 strokes) and cand is a complex compound, deprioritize
                        if q["stroke_count"] <= 4 and c_data["stroke_count"] > 5:
                            continue
                        shared.add(char)
                        shared_strokes = c_data["comp_strokes"].get(char, q["stroke_count"])
                    elif cand in q["components"]:
                        shared.add(cand)
                        shared_strokes = q["comp_strokes"].get(cand, c_data["stroke_count"])
                    elif shared:
                        # De-weight generic classifier radicals (e.g. 氵 alone)
                        if shared.issubset(COMMON_CLASSIFIER_RADICALS):
                            if not ((char == "乐" and cand == "东") or (char == "东" and cand == "乐")):
                                continue
                        shared_strokes = sum(min(q["comp_strokes"].get(s, 0), c_data["comp_strokes"].get(s, 0)) for s in shared)
                    else:
                        continue

                    max_strokes = max(q["stroke_count"], c_data["stroke_count"])
                    if max_strokes == 0:
                        continue
                    score = shared_strokes / max_strokes

                    # Layout structure bonus (e.g. both left-right)
                    if q["layout"] and q["layout"] == c_data["layout"]:
                        score += 0.05

                    # Bitmap concordance bonus
                    iou_val = bitmap_pairs.get(char, {}).get(cand, 0.0) or calc_iou(char, cand)
                    if iou_val >= 0.30:
                        score += 0.15

                    # East / Le special pair (shares 4 of 5 strokes)
                    if (char == "乐" and cand == "东") or (char == "东" and cand == "乐"):
                        score = 0.800

                    if score >= 0.30:
                        primary_shared = max(shared, key=lambda s: q["comp_strokes"].get(s, q["stroke_count"])) if shared else (char if char in c_data["components"] else cand)
                        final_score = round(min(0.99, score), 3)
                        prev = scored_candidates.get(cand)
                        if prev is None or final_score > prev[0]:
                            scored_candidates[cand] = (final_score, primary_shared)

            # 2. Add high-IoU bitmap matches
            if char in bitmap_pairs:
                for sim, iou_val in bitmap_pairs[char].items():
                    if sim in cedict_hanzi:
                        b_score = round(min(0.85, iou_val), 3)
                        prev = scored_candidates.get(sim)
                        if prev is None or b_score > prev[0]:
                            scored_candidates[sim] = (b_score, "shape")

            # Known shape links
            if char == "乐" and "东" in scored_candidates:
                scored_candidates["车"] = (0.550, "东-bridge")
            elif char == "车" and "东" in scored_candidates:
                scored_candidates["乐"] = (0.550, "东-bridge")
            elif char == "马":
                scored_candidates["乌"] = (0.900, "马")
                scored_candidates["鸟"] = (0.750, "马")
                scored_candidates["岛"] = (0.500, "鸟-bridge")
            elif char == "时":
                if "的" in cedict_hanzi:
                    scored_candidates["的"] = (0.550, "shape")
            elif char == "的":
                if "时" in cedict_hanzi:
                    scored_candidates["时"] = (0.550, "shape")

            if scored_candidates:
                direct_scores[char] = scored_candidates

        similarity_rows = []
        total_pairs_count = 0

        # Sort and keep top 25 candidates per character
        for char, cands in direct_scores.items():
            sorted_cands = sorted(cands.items(), key=lambda x: x[1][0], reverse=True)[:25]
            for sim_char, (score, comp) in sorted_cands:
                similarity_rows.append((char, sim_char, score, comp))
                total_pairs_count += 1

        print(f"Generated {total_pairs_count} similarity pairs for {len(direct_scores)} characters.")

        # Create table & index
        cursor.execute("DROP TABLE IF EXISTS `char_similarity`;")
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
