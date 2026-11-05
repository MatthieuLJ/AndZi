#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Automated script to measure exact intro and outro offsets for all Slow Chinese episodes
using faster-whisper word-level alignment against the episode RSS transcripts.
Outputs: slow_chinese_offsets.json
"""

import os
import sys
import json
import subprocess
import xml.etree.ElementTree as ET
import re
from faster_whisper import WhisperModel

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
AUDIO_DIR = os.path.join(PROJECT_ROOT, "audio/slowchinese")
RSS_FILE = "/tmp/slowchinese.rss"
OUTPUT_JSON = os.path.join(SCRIPT_DIR, "slow_chinese_offsets.json")

def get_audio_duration(file_path):
    proc = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file_path],
        capture_output=True, text=True
    )
    return float(proc.stdout.strip())

def extract_audio_slice(input_path, start_s, end_s, output_path):
    cmd = [
        "ffmpeg", "-y", "-ss", str(max(0, start_s)), "-to", str(end_s),
        "-i", input_path, "-ac", "1", "-ar", "16000", output_path
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def clean_transcript(text):
    text = re.sub(r"<[^>]+>", " ", text)
    # Filter only chinese lines
    lines = [l.strip() for l in text.split("\n") if l.strip()]
    return lines

def find_target_sentences(lines):
    # First sentence with Hanzi
    first_sentence = ""
    for l in lines:
        hz = [c for c in l if "\u4e00" <= c <= "\u9fff"]
        if len(hz) >= 5:
            # Take first ~12 characters
            first_sentence = "".join(hz[:12])
            break

    # Last sentence with Hanzi
    last_sentence = ""
    for l in reversed(lines):
        hz = [c for c in l if "\u4e00" <= c <= "\u9fff"]
        if len(hz) >= 4:
            # Check if this line is music/singer credit
            line_str = "".join(hz)
            if any(k in line_str for k in ["音乐", "演唱", "演奏", "作曲", "作词", "歌手"]):
                continue
            last_sentence = "".join(hz[-12:])
            break

    return first_sentence, last_sentence

def match_chars(target, transcribed):
    # Check simple substring or high overlap
    if not target or not transcribed:
        return False
    match_cnt = 0
    for c in target:
        if c in transcribed:
            match_cnt += 1
    return (match_cnt / len(target)) >= 0.65

def main():
    if not os.path.exists(RSS_FILE):
        print(f"Error: {RSS_FILE} does not exist. Please download it first.")
        sys.exit(1)

    print("Parsing RSS feed...")
    tree = ET.parse(RSS_FILE)
    items = tree.getroot().find("channel").findall("item")

    # Build episode map: ep_num -> (title, first_sentence, last_sentence)
    episodes_data = {}
    for it in items:
        title = it.find("title").text
        # match episode number
        m = re.match(r"^\s*(\d+)", title)
        if not m:
            m = re.search(r"#\s*(\d+)", title)
        if m:
            ep_num = int(m.group(1))
            desc = it.find("description").text or ""
            lines = clean_transcript(desc)
            f_sent, l_sent = find_target_sentences(lines)
            episodes_data[ep_num] = {
                "title": title,
                "first_sent": f_sent,
                "last_sent": l_sent
            }

    print(f"Found {len(episodes_data)} episodes in RSS.")

    print("Loading faster-whisper model (small)...")
    model = WhisperModel("small", device="cpu", compute_type="int8")

    results = {}
    temp_wav = "/tmp/measure_temp.wav"

    ep_keys = sorted(episodes_data.keys())
    # You can process all or test a subset
    print(f"Processing {len(ep_keys)} episodes...")

    for ep_num in ep_keys:
        mp3_name = f"Slow_Chinese_{ep_num:03d}.mp3"
        mp3_path = os.path.join(AUDIO_DIR, mp3_name)
        if not os.path.exists(mp3_path):
            continue

        total_dur = get_audio_duration(mp3_path)
        ep_info = episodes_data[ep_num]
        f_sent = ep_info["first_sent"]
        l_sent = ep_info["last_sent"]

        # 1. MEASURE INTRO (Transcribe first 45 seconds)
        intro_slice_end = min(45.0, total_dur)
        extract_audio_slice(mp3_path, 0.0, intro_slice_end, temp_wav)
        segments, _ = model.transcribe(temp_wav, language="zh", word_timestamps=True)

        measured_intro_s = 25.0 # fallback
        found_intro = False
        for seg in segments:
            # check segment text or words
            seg_hz = "".join([c for c in seg.text if "\u4e00" <= c <= "\u9fff"])
            if match_chars(f_sent, seg_hz):
                # find first word that matches
                for w in seg.words:
                    w_hz = "".join([c for c in w.word if "\u4e00" <= c <= "\u9fff"])
                    if any(c in f_sent for c in w_hz):
                        measured_intro_s = w.start
                        found_intro = True
                        break
            if found_intro:
                break

        # 2. MEASURE OUTRO
        # Search backwards in slices of 40s from total_dur
        # (Handling music tracks up to 5 mins)
        measured_story_end_s = total_dur - 10.0
        found_outro = False
        
        # Test windows: [total_dur-45, total_dur], [total_dur-90, total_dur-40], [total_dur-180, total_dur-80], [total_dur-300, total_dur-170]
        windows = [
            (max(0, total_dur - 45), total_dur),
            (max(0, total_dur - 95), max(0, total_dur - 40)),
            (max(0, total_dur - 185), max(0, total_dur - 85)),
            (max(0, total_dur - 305), max(0, total_dur - 175)),
        ]

        for w_start, w_end in windows:
            if w_end <= measured_intro_s:
                break
            extract_audio_slice(mp3_path, w_start, w_end, temp_wav)
            segments, _ = model.transcribe(temp_wav, language="zh", word_timestamps=True)
            for seg in segments:
                seg_hz = "".join([c for c in seg.text if "\u4e00" <= c <= "\u9fff"])
                if match_chars(l_sent, seg_hz):
                    # Find end of matching words
                    last_end = seg.end
                    for w in seg.words:
                        w_hz = "".join([c for c in w.word if "\u4e00" <= c <= "\u9fff"])
                        if any(c in l_sent for c in w_hz):
                            last_end = max(last_end, w.end)
                    measured_story_end_s = w_start + last_end
                    found_outro = True
                    break
            if found_outro:
                break

        measured_outro_s = max(2.0, total_dur - measured_story_end_s)

        intro_ms = int(round(measured_intro_s * 1000))
        outro_ms = int(round(measured_outro_s * 1000))

        results[ep_num] = {
            "title": ep_info["title"],
            "total_dur_s": round(total_dur, 2),
            "intro_offset_ms": intro_ms,
            "outro_offset_ms": outro_ms,
            "story_start_s": round(measured_intro_s, 2),
            "story_end_s": round(measured_story_end_s, 2),
        }

        print(f"[{ep_num:03d}/204] Intro: {intro_ms:5d}ms ({measured_intro_s:.2f}s) | Outro: {outro_ms:6d}ms ({measured_outro_s:.2f}s) | {ep_info['title'][:30]}")

        # Save progress incrementally
        with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\nDone! Saved all offsets to {OUTPUT_JSON}")

if __name__ == "__main__":
    main()

