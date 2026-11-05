#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Download all Slow Chinese (慢速中文) and Reading China (中文天天读) podcast MP3 audio files from the Internet Archive.
Files are saved to audio/slowchinese/ and audio/readingchina/ in the project root.
Both directories and *.mp3 files are excluded from git tracking.
"""

import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
SLOWCHINESE_DIR = os.path.join(ROOT_DIR, "audio/slowchinese")
READINGCHINA_DIR = os.path.join(ROOT_DIR, "audio/readingchina")

SLOWCHINESE_BASE = "https://archive.org/download/slowchinese_201909"
READINGCHINA_BASE = "https://archive.org/download/readingchina"

def download_item(task):
    url, dest_path, label = task
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 50 * 1024:
        return label, "CACHED", os.path.getsize(dest_path)

    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    req = urllib.request.Request(url, headers=headers)

    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=35) as resp:
                if resp.status != 200:
                    raise Exception(f"HTTP status {resp.status}")
                temp_path = dest_path + ".tmp"
                with open(temp_path, "wb") as f_out:
                    while True:
                        chunk = resp.read(64 * 1024)
                        if not chunk:
                            break
                        f_out.write(chunk)
                os.replace(temp_path, dest_path)
                return label, "DOWNLOADED", os.path.getsize(dest_path)
        except Exception as e:
            if attempt == 3:
                return label, f"FAILED: {e}", 0
            time.sleep(1.5 * attempt)

def main():
    os.makedirs(SLOWCHINESE_DIR, exist_ok=True)
    os.makedirs(READINGCHINA_DIR, exist_ok=True)

    tasks = []

    # 1. 204 Slow Chinese episodes
    for ep_id in range(1, 205):
        filename = f"Slow_Chinese_{ep_id:03d}.mp3"
        url = f"{SLOWCHINESE_BASE}/{filename}"
        dest_path = os.path.join(SLOWCHINESE_DIR, filename)
        tasks.append((url, dest_path, f"Slow Chinese {ep_id:03d}"))

    # 2. 20 Reading China episodes
    for ep_id in range(1, 21):
        filename = f"Reading_China_{ep_id:02d}.mp3"
        url = f"{READINGCHINA_BASE}/{filename}"
        dest_path = os.path.join(READINGCHINA_DIR, filename)
        tasks.append((url, dest_path, f"Reading China {ep_id:02d}"))

    total = len(tasks)
    print(f"Starting download of {total} audio episodes ({len(tasks)-20} Slow Chinese + 20 Reading China)...")
    print(f"Destination: {SLOWCHINESE_DIR}")

    completed = 0
    total_bytes = 0
    failed = []

    with ThreadPoolExecutor(max_workers=8) as executor:
        future_map = {executor.submit(download_item, t): t for t in tasks}
        for future in as_completed(future_map):
            completed += 1
            label, status, size = future.result()
            total_bytes += size
            size_mb = size / (1024 * 1024)
            if status == "DOWNLOADED":
                print(f"[{completed:03d}/{total}] ✓ Downloaded {label} ({size_mb:.1f} MB)")
            elif status == "CACHED":
                print(f"[{completed:03d}/{total}] ⏩ Already cached {label} ({size_mb:.1f} MB)")
            else:
                print(f"[{completed:03d}/{total}] ❌ {label}: {status}")
                failed.append(label)

    print("\n" + "=" * 50)
    print(f"Finished! Successfully saved: {completed - len(failed)} / {total} episodes.")
    print(f"Total archive size: {total_bytes / (1024 * 1024):.1f} MB")
    if failed:
        print(f"Failed ({len(failed)}): {failed}")
    print("=" * 50)

if __name__ == "__main__":
    main()

