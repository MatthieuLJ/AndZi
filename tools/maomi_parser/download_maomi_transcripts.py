#!/usr/bin/env python3
"""
download_maomi_transcripts.py

Crawls MaoMi Chinese Buzzsprout RSS and WordPress REST API to assemble
all public episodes (1-271) and their transcripts into a bundled asset JSON
for offline ingestion and HSK grading in AndZi.
"""

import email.utils
import html
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

RSS_URL = "https://feeds.buzzsprout.com/1426696.rss"
WP_API_URL = "https://maomichinese.com/wp-json/wp/v2/posts"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
OUTPUT_PATH = os.path.join(PROJECT_ROOT, "app/src/main/assets/maomi_transcripts.json")
BACKUP_134_PATH = "/tmp/maomi_134_transcript.txt"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Connection": "close"
}


def parse_duration_to_ms(dur_str: str) -> int:
    if not dur_str:
        return 0
    dur_str = dur_str.strip()
    if ":" in dur_str:
        parts = dur_str.split(":")
        if len(parts) == 3:
            return (int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])) * 1000
        elif len(parts) == 2:
            return (int(parts[0]) * 60 + int(parts[1])) * 1000
    try:
        return int(float(dur_str)) * 1000
    except ValueError:
        return 0


def parse_pubdate_to_ms(date_str: str) -> int:
    if not date_str:
        return 0
    try:
        dt = email.utils.parsedate_to_datetime(date_str)
        return int(dt.timestamp() * 1000)
    except Exception:
        return 0


def fetch_rss_episodes():
    print(f"Fetching RSS feed from {RSS_URL}...")
    req = urllib.request.Request(RSS_URL, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=20) as resp:
        xml_bytes = resp.read()

    root = ET.fromstring(xml_bytes)
    channel = root.find("channel")
    if channel is None:
        raise ValueError("Invalid RSS: no channel element")

    rss_episodes = {}
    for item in channel.findall("item"):
        raw_title = item.findtext("title") or ""
        title = html.unescape(raw_title).strip()

        itunes_ep = item.findtext("{http://www.itunes.com/dtds/podcast-1.0.dtd}episode")
        ep_num = None
        if itunes_ep and itunes_ep.strip().isdigit():
            ep_num = int(itunes_ep.strip())
        else:
            m = re.search(r"#(\d+)", title)
            if m:
                ep_num = int(m.group(1))

        if not ep_num:
            continue

        enclosure = item.find("enclosure")
        audio_url = enclosure.attrib.get("url", "") if enclosure is not None else ""

        dur_text = (
            item.findtext("{http://www.itunes.com/dtds/podcast-1.0.dtd}duration")
            or item.findtext("duration")
            or ""
        )
        duration_ms = parse_duration_to_ms(dur_text)

        pub_text = item.findtext("pubDate") or ""
        pub_date_ms = parse_pubdate_to_ms(pub_text)

        rss_episodes[ep_num] = {
            "episodeNum": ep_num,
            "title": title,
            "audioUrl": audio_url,
            "durationMs": duration_ms,
            "pubDate": pub_date_ms,
        }

    print(f"Parsed {len(rss_episodes)} public episodes from RSS (min: {min(rss_episodes)}, max: {max(rss_episodes)})")
    return rss_episodes


def extract_paragraphs_from_html(html_str: str) -> list[str]:
    paras = re.findall(r"<p[^>]*>(.*?)</p>", html_str, re.DOTALL)
    clean = []
    for p in paras:
        # Strip all HTML tags
        c = re.sub(r"<[^>]+>", "", p)
        c = html.unescape(c).strip()
        c = re.sub(r"\s+", " ", c)
        if c and c != "\xa0" and c != "&nbsp;":
            clean.append(c)
    return clean


def fetch_wp_transcripts(known_episodes: set[int]):
    import subprocess
    print("Fetching WordPress posts from maomichinese.com via curl (HTTP/2)...")
    wp_posts = []
    page = 1
    per_page = 50
    while True:
        url = f"{WP_API_URL}?per_page={per_page}&page={page}"
        cmd = ["curl", "-s", "--max-time", "30", url]
        try:
            out = subprocess.check_output(cmd)
            data = json.loads(out.decode("utf-8"))
            if not isinstance(data, list) or not data:
                break
            wp_posts.extend(data)
            print(f"  Fetched page {page} ({len(data)} posts, total so far: {len(wp_posts)})")
            if len(data) < per_page:
                break
            page += 1
        except Exception as e:
            print(f"  Error on page {page}: {e}")
            break

    print(f"Total WP posts fetched: {len(wp_posts)}")

    transcripts = {}
    for post in wp_posts:
        slug = post.get("slug", "")
        title_raw = post.get("title", {}).get("rendered", "")
        title = html.unescape(title_raw).strip()

        # Filter out member-only posts unless title specifically corresponds to a public RSS episode
        if title.lower().startswith("members"):
            continue

        ep_num = None
        m_title = re.search(r"#(\d+)", title)
        if m_title:
            ep_num = int(m_title.group(1))
        else:
            m_slug = re.search(r"(?:podcast|poadcast)(\d+)", slug)
            if m_slug:
                ep_num = int(m_slug.group(1))
            else:
                m_slug2 = re.search(r"^(\d+)-", slug)
                if m_slug2:
                    ep_num = int(m_slug2.group(1))

        if not ep_num or ep_num not in known_episodes:
            continue

        content = post.get("content", {}).get("rendered", "")

        zh_text = ""
        en_text = ""

        # Strategy 1: Modern layout with mme-lang-zh and mme-lang-en classes
        m_zh = re.search(r'<div[^>]*class="[^"]*mme-lang-zh[^"]*"[^>]*>(.*?)</div>', content, re.DOTALL)
        m_en = re.search(r'<div[^>]*class="[^"]*mme-lang-en[^"]*"[^>]*>(.*?)</div>', content, re.DOTALL)

        # Strategy 2: Elementor tabs with id="chinese-tab" and id="english-tab"
        if not m_zh:
            m_zh = re.search(r'<div[^>]*id="chinese-tab"[^>]*>(.*?)</div>\s*(?:<div id=|</div)', content, re.DOTALL)
        if not m_en:
            m_en = re.search(r'<div[^>]*id="english-tab"[^>]*>(.*?)</div>\s*(?:<div id=|</div)', content, re.DOTALL)

        if m_zh:
            paras = extract_paragraphs_from_html(m_zh.group(1))
            zh_text = "\n\n".join(paras)
        if m_en:
            paras = extract_paragraphs_from_html(m_en.group(1))
            en_text = "\n\n".join(paras)

        # Strategy 3: Older layout, fallback to all paragraphs containing Chinese
        if not zh_text:
            paras = extract_paragraphs_from_html(content)
            zh_paras = [p for p in paras if re.search(r"[\u4e00-\u9fff]", p)]
            # Filter boilerplate
            boilerplate = ["MaoMi Chinese 是一个", "支持我们", "加入会员", "微信公众号", "感谢收听"]
            filtered = [p for p in zh_paras if not any(b in p for b in boilerplate)]
            if filtered:
                zh_text = "\n\n".join(filtered)

        if zh_text:
            transcripts[ep_num] = {
                "transcript": zh_text,
                "translation": en_text,
                "wpSlug": slug,
            }

    print(f"Extracted transcripts for {len(transcripts)} episodes from WordPress")
    return transcripts


def main():
    rss_episodes = fetch_rss_episodes()
    known_nums = set(rss_episodes.keys())

    wp_transcripts = fetch_wp_transcripts(known_nums)

    # Check for missing episodes
    missing = sorted(list(known_nums - set(wp_transcripts.keys())))
    if missing:
        print(f"Episodes missing from WordPress: {missing}")
        # Episode 134 fallback
        if 134 in missing and os.path.exists(BACKUP_134_PATH):
            print(f"Loading episode 134 transcript from local whisper backup: {BACKUP_134_PATH}")
            with open(BACKUP_134_PATH, "r", encoding="utf-8") as f:
                backup_text = f.read().strip()
            if backup_text:
                wp_transcripts[134] = {
                    "transcript": backup_text,
                    "translation": "",
                    "wpSlug": "whisper-fallback",
                }
                missing.remove(134)

    # Assemble dataset
    combined = []
    for ep_num in sorted(known_nums):
        ep_info = rss_episodes[ep_num]
        tr_info = wp_transcripts.get(ep_num, {"transcript": "", "translation": ""})

        combined.append({
            "episodeNum": ep_num,
            "title": ep_info["title"],
            "audioUrl": ep_info["audioUrl"],
            "durationMs": ep_info["durationMs"],
            "pubDate": ep_info["pubDate"],
            "transcript": tr_info["transcript"],
            "translation": tr_info.get("translation", ""),
        })

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(combined, f, ensure_ascii=False, indent=2)

    total_with_transcript = sum(1 for ep in combined if ep["transcript"])
    file_size_kb = os.path.getsize(OUTPUT_PATH) / 1024

    print("\n--- Summary ---")
    print(f"Total episodes: {len(combined)}")
    print(f"Episodes with transcript: {total_with_transcript} / {len(combined)} ({total_with_transcript / len(combined) * 100:.1f}%)")
    print(f"Saved to: {OUTPUT_PATH} ({file_size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
