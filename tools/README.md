# AndZi Data Preparation & Update Tools

This directory contains Python 3 utilities for generating, updating, and maintaining offline databases and bundled assets in AndZi.

---

## 1. Updating Pre-fetched Podcast Databases

When releasing a new version of the app or refreshing episodes for offline listening, use [`tools/update_podcast_database.py`](update_podcast_database.py).

### Commands

```bash
# Update and re-enrich all podcast channels (MaoMi Chinese + TeaTime Chinese)
python3 tools/update_podcast_database.py

# Validate existing bundled assets without re-downloading
python3 tools/update_podcast_database.py --check

# Update only MaoMi Chinese (Channel 3)
python3 tools/update_podcast_database.py --maomi

# Update only TeaTime Chinese (Channel 4)
python3 tools/update_podcast_database.py --teatime
```

### What this script does:
1. **MaoMi Chinese (`app/src/main/assets/maomi_transcripts.json`)**:
   - Crawls Buzzsprout RSS and WordPress REST API for newly published episodes.
   - Performs CC-CEDICT vocabulary segmentation and computes target HSK levels.
2. **TeaTime Chinese (`app/src/main/assets/teatime_transcripts.json`)**:
   - Downloads catalog and official SRT subtitles from S3 with millisecond precision (`start_ms`, `end_ms`).
   - Performs CC-CEDICT vocabulary segmentation and assigns HSK levels.
3. **Validation**:
   - Checks JSON structure, verifies no duplicate episode numbers, ensures non-empty transcripts, and validates vocabulary and sentence counts.

---

## 2. CC-CEDICT Dictionary Database (`tools/cedict_parser/`)

Used for parsing raw CC-CEDICT text and compiling the reference dictionary:

```bash
cd tools/cedict_parser
./do_parse_cedict.sh
```

This compiles:
- `app/src/main/res/raw/cedict_db.zip` (compressed offline SQLite database with HSK tags, visual similarity, and stroke order)
