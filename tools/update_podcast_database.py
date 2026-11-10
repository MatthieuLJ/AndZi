#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
tools/update_podcast_database.py

Unified maintenance script to update and validate the pre-fetched offline podcast databases
(MaoMi Chinese and TeaTime Chinese) bundled in app/src/main/assets/.

Usage:
  python3 tools/update_podcast_database.py              # Update and enrich all channels
  python3 tools/update_podcast_database.py --check      # Validate existing assets without re-downloading
  python3 tools/update_podcast_database.py --maomi      # Update only MaoMi Chinese
  python3 tools/update_podcast_database.py --teatime    # Update only TeaTime Chinese
"""

import argparse
import json
import os
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app/src/main/assets")

MAOMI_ASSET = os.path.join(ASSETS_DIR, "maomi_transcripts.json")
TEATIME_ASSET = os.path.join(ASSETS_DIR, "teatime_transcripts.json")

MAOMI_DOWNLOAD_SCRIPT = os.path.join(SCRIPT_DIR, "maomi_parser/download_maomi_transcripts.py")
MAOMI_ENRICH_SCRIPT = os.path.join(SCRIPT_DIR, "maomi_parser/enrich_maomi_transcripts.py")
TEATIME_BUILD_SCRIPT = os.path.join(SCRIPT_DIR, "teatime_parser/build_teatime_transcripts.py")


def log_header(title: str):
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70)


def log_step(msg: str):
    print(f"==> {msg}")


def log_success(msg: str):
    print(f" [PASS] {msg}")


def log_error(msg: str):
    print(f" [FAIL] {msg}", file=sys.stderr)


def validate_asset(name: str, file_path: str, min_episodes: int, min_vocab: int, require_sentences: bool = False) -> bool:
    log_step(f"Validating {name} ({file_path})...")
    if not os.path.exists(file_path):
        log_error(f"Asset file not found: {file_path}")
        return False

    size_mb = os.path.getsize(file_path) / 1024 / 1024
    if size_mb < 0.1:
        log_error(f"File size suspiciously small: {size_mb:.2f} MB")
        return False

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        log_error(f"Failed to parse JSON: {e}")
        return False

    if not isinstance(data, list):
        log_error("Asset root element must be a JSON array")
        return False

    total_episodes = len(data)
    if total_episodes < min_episodes:
        log_error(f"Episode count ({total_episodes}) below expected minimum ({min_episodes})")
        return False

    ep_nums = [ep.get("episodeNum") for ep in data]
    if len(ep_nums) != len(set(ep_nums)):
        duplicates = [x for x in ep_nums if ep_nums.count(x) > 1]
        log_error(f"Duplicate episode numbers found: {set(duplicates)}")
        return False

    total_vocab = sum(len(ep.get("vocab", [])) for ep in data)
    if total_vocab < min_vocab:
        log_error(f"Total vocabulary count ({total_vocab}) below expected minimum ({min_vocab})")
        return False

    total_sentences = 0
    if require_sentences:
        total_sentences = sum(len(ep.get("sentences", [])) for ep in data)
        if total_sentences < 1000:
            log_error(f"Total sentences count ({total_sentences}) below expected minimum")
            return False

    with_transcripts = sum(1 for ep in data if ep.get("transcript", "").strip())
    transcript_pct = (with_transcripts / total_episodes) * 100

    print(f"       Episodes: {total_episodes} (min: {min(ep_nums)}, max: {max(ep_nums)})")
    print(f"       With Transcripts: {with_transcripts} / {total_episodes} ({transcript_pct:.1f}%)")
    print(f"       Vocabulary Entries: {total_vocab:,}")
    if require_sentences:
        print(f"       Sentences with Timestamps: {total_sentences:,}")
    print(f"       Asset File Size: {size_mb:.2f} MB")

    log_success(f"{name} asset validation passed!")
    return True


def update_maomi() -> bool:
    log_header("Updating MaoMi Chinese (Channel 3)")
    t0 = time.time()

    # Step 1: Download transcripts from RSS & WordPress
    log_step("Step 1/2: Crawling MaoMi RSS and WordPress API...")
    ret = subprocess.run([sys.executable, MAOMI_DOWNLOAD_SCRIPT], cwd=PROJECT_ROOT)
    if ret.returncode != 0:
        log_error(f"download_maomi_transcripts.py failed with return code {ret.returncode}")
        return False

    # Step 2: Enrich with CC-CEDICT vocabulary segmentation and HSK levels
    log_step("Step 2/2: Segmenting vocabulary & grading HSK levels via CC-CEDICT...")
    ret = subprocess.run([sys.executable, MAOMI_ENRICH_SCRIPT], cwd=PROJECT_ROOT)
    if ret.returncode != 0:
        log_error(f"enrich_maomi_transcripts.py failed with return code {ret.returncode}")
        return False

    # Validate output
    ok = validate_asset("MaoMi Chinese", MAOMI_ASSET, min_episodes=270, min_vocab=30000)
    print(f"MaoMi Chinese update finished in {time.time() - t0:.1f}s")
    return ok


def update_teatime() -> bool:
    log_header("Updating TeaTime Chinese (Channel 4)")
    t0 = time.time()

    log_step("Fetching TeaTime catalog, SRT timestamps, and segmenting CC-CEDICT vocabulary...")
    ret = subprocess.run([sys.executable, TEATIME_BUILD_SCRIPT], cwd=PROJECT_ROOT)
    if ret.returncode != 0:
        log_error(f"build_teatime_transcripts.py failed with return code {ret.returncode}")
        return False

    # Validate output
    ok = validate_asset("TeaTime Chinese", TEATIME_ASSET, min_episodes=100, min_vocab=20000, require_sentences=True)
    print(f"TeaTime Chinese update finished in {time.time() - t0:.1f}s")
    return ok


def main():
    parser = argparse.ArgumentParser(
        description="Update pre-fetched offline podcast databases in app/src/main/assets/"
    )
    parser.add_argument("--check", action="store_true", help="Validate existing asset files without re-downloading")
    parser.add_argument("--maomi", action="store_true", help="Update only MaoMi Chinese (channel 3)")
    parser.add_argument("--teatime", action="store_true", help="Update only TeaTime Chinese (channel 4)")

    args = parser.parse_args()

    # If --check is supplied, just validate existing assets
    if args.check:
        log_header("Validating Pre-fetched Offline Databases")
        ok_maomi = validate_asset("MaoMi Chinese", MAOMI_ASSET, min_episodes=270, min_vocab=30000)
        ok_teatime = validate_asset("TeaTime Chinese", TEATIME_ASSET, min_episodes=100, min_vocab=20000, require_sentences=True)
        if ok_maomi and ok_teatime:
            log_header("All pre-fetched database assets are valid and healthy!")
            sys.exit(0)
        else:
            log_error("One or more assets failed validation.")
            sys.exit(1)

    update_all = not (args.maomi or args.teatime)

    success = True
    if update_all or args.maomi:
        if not update_maomi():
            success = False

    if update_all or args.teatime:
        if not update_teatime():
            success = False

    log_header("Update Summary")
    if success:
        log_success("Pre-fetched podcast databases updated successfully!")
    else:
        log_error("One or more podcast database updates failed.")
        sys.exit(1)


if __name__ == "__main__":
    main()
