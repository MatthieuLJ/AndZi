#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Test character similarity tables against ground-truth golden test cases.
Verifies that key visual confusable pairs are retrieved and ranked appropriately,
and that generic radical floods are suppressed.
"""

import sqlite3
import tempfile
import zipfile
import sys

GOLDEN_TEST_CASES = {
    "乐": {
        "must_include": ["东", "车", "泺"],
        "must_not_top": ["尕", "示", "田", "永"],
    },
    "马": {
        "must_include": ["乌", "鸟", "岛"],
        "must_not_top": ["驰", "驻", "驷", "骀", "驸"],
    },
    "已": {
        "must_include": ["己", "巳", "巴", "㔾"],
        "must_not_top": ["民", "巨", "円"],
    },
    "经": {
        "must_include": ["轻"],
        "must_not_top": ["邛", "攻", "巩"],
    },
    "汤": {
        "must_include": ["场", "杨", "饧"],
        "must_not_top": ["江", "池", "污", "汛", "汲", "汗"],
    },
    "时": {
        "must_include": ["的"],
        "must_not_top": ["旧", "汨", "阳"],
    },
    "候": {
        "must_include": ["侯"],
        "must_not_top": ["他", "你", "休"],
    },
    "真": {
        "must_include": ["直", "值"],
        "must_not_top": [],
    },
}

def main():
    zip_path = "app/src/main/res/raw/cedict_db.zip"
    with zipfile.ZipFile(zip_path, "r") as z:
        with z.open(z.namelist()[0]) as f:
            with tempfile.NamedTemporaryFile() as tf:
                tf.write(f.read())
                tf.flush()
                conn = sqlite3.connect(tf.name)
                cursor = conn.cursor()

                total_tests = 0
                passed_tests = 0

                print(f"=== Testing Character Similarity Database ({zip_path}) ===\n")

                for target, expectations in GOLDEN_TEST_CASES.items():
                    cursor.execute(
                        "SELECT similar_hanzi, score, shared_component FROM char_similarity WHERE hanzi = ? ORDER BY score DESC",
                        (target,)
                    )
                    rows = cursor.fetchall()
                    top_chars = [r[0] for r in rows]
                    top_scores = {r[0]: r[1] for r in rows}
                    top10 = top_chars[:10]

                    print(f"Target: 【 {target} 】 (Total retrieved: {len(rows)})")
                    print(f"  Top 8: {', '.join(f'{r[0]} ({r[1]:.3f})' for r in rows[:8])}")

                    # Check must_include
                    for expected in expectations.get("must_include", []):
                        total_tests += 1
                        if expected in top_chars:
                            rank = top_chars.index(expected) + 1
                            score = top_scores[expected]
                            print(f"  ✓ Found '{expected}' at rank {rank} (score: {score:.3f})")
                            passed_tests += 1
                        else:
                            print(f"  ✗ MISSING: '{expected}' not found in similarity list!")

                    # Check must_not_top (should not dominate the top 10)
                    for bad in expectations.get("must_not_top", []):
                        total_tests += 1
                        if bad in top10:
                            rank = top10.index(bad) + 1
                            print(f"  ✗ UNWANTED in Top 10: '{bad}' is at rank {rank}!")
                        else:
                            passed_tests += 1

                    print()

                conn.close()

                print(f"Test Summary: {passed_tests} / {total_tests} checks passed ({(passed_tests/total_tests)*100:.1f}%)")
                if passed_tests < total_tests:
                    sys.exit(1)

if __name__ == "__main__":
    main()

