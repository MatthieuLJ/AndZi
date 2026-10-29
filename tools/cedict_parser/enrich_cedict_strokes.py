#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Enrich CC-CEDICT SQLite database with all 9,574 character stroke geometries and medians
from assets/makemeahanzi/graphics.txt.
Updates cedict_db to version 10 and re-compresses app/src/main/res/raw/cedict_db.zip.
"""

import json
import os
import sqlite3
import tempfile
import time
import zipfile

ROOM_IDENTITY_HASH = "6f1b0c0409fc7612c232a3cfaae51f63"

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    raw_res_dir = os.path.join(root_dir, "app/src/main/res/raw")
    existing_zip = os.path.join(raw_res_dir, "cedict_db.zip")
    output_zip = os.path.join(raw_res_dir, "cedict_db.zip")
    graphics_path = os.path.join(root_dir, "assets/makemeahanzi/graphics.txt")

    if not os.path.exists(existing_zip):
        raise FileNotFoundError(f"Source database zip not found at {existing_zip}")
    if not os.path.exists(graphics_path):
        raise FileNotFoundError(f"Graphics data not found at {graphics_path}")

    t0 = time.time()
    with tempfile.TemporaryDirectory() as tmpdir:
        extracted_db = os.path.join(tmpdir, "cedict_db")

        print(f"Extracting existing dictionary from {existing_zip}...")
        with zipfile.ZipFile(existing_zip, 'r') as z:
            name = z.namelist()[0]
            with open(extracted_db, 'wb') as f:
                f.write(z.read(name))

        conn = sqlite3.connect(extracted_db)
        cursor = conn.cursor()

        print("Creating character_strokes table...")
        cursor.execute("DROP TABLE IF EXISTS `character_strokes`;")
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS `character_strokes` (
                `hanzi` TEXT NOT NULL,
                `strokes` TEXT NOT NULL,
                `medians` TEXT NOT NULL,
                `stroke_count` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`hanzi`)
            );
        """)

        print(f"Reading graphics from {graphics_path}...")
        rows = []
        with open(graphics_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                ch = d.get("character")
                strokes = d.get("strokes", [])
                medians = d.get("medians", [])
                if ch and strokes:
                    rows.append((
                        ch,
                        json.dumps(strokes, separators=(',', ':')),
                        json.dumps(medians, separators=(',', ':')),
                        len(strokes)
                    ))

        print(f"Inserting {len(rows)} character strokes...")
        cursor.executemany(
            "INSERT OR REPLACE INTO `character_strokes` (`hanzi`, `strokes`, `medians`, `stroke_count`) VALUES (?, ?, ?, ?)",
            rows
        )

        # Update Room master table and user_version to 10
        cursor.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
        cursor.execute(f"INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '{ROOM_IDENTITY_HASH}');")
        cursor.execute("PRAGMA user_version = 10;")

        conn.commit()
        print("Vacuuming database...")
        cursor.execute("VACUUM;")
        conn.close()

        print(f"Compressing updated database into {output_zip}...")
        with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            z.write(extracted_db, arcname="cedict_db")

        out_size = os.path.getsize(output_zip)
        print(f"Done in {time.time() - t0:.2f}s! Successfully created {output_zip} ({out_size:,} bytes).")

if __name__ == "__main__":
    main()

