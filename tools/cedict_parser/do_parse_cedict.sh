#!/bin/bash

rm cedict_db
time python3 ./import_cedict.py cedict_ts.u8 cedict_db
