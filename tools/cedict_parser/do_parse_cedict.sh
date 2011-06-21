#!/bin/bash

rm cedict_db
time ./import_cedict.py cedict_1_0_ts_utf-8_mdbg.txt cedict_db
