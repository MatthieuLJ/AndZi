#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import os
import re
import codecs
import string
import sqlite3
import CharDB

if (len(sys.argv) != 3):
    print "not enough argument "+sys.argv[0]+" charData database"
    sys.exit()

try:
    char_data_file=codecs.open(sys.argv[1], "r", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[1]
    sys.exit()

with open(sys.argv[1], "r") as f:
    for i, l in enumerate(f):
        pass
    total_num_lines=i+1
current_line=0

if (os.path.exists(sys.argv[2])):
    os.remove(sys.argv[2])

db=CharDB.char_db(sys.argv[2])
while True:
    try:
        line = char_data_file.next()
    except StopIteration:
        sys.exit()

    m = re.match(r"(.+):([^/]+)/*(.*)\((.*)\)", line)

    current_line += 1
    if (current_line % 10 == 0):
        sys.stdout.write("Processing line "+str(current_line)+" out of "+str(total_num_lines)+"\r")
        sys.stdout.flush()

    if m is None:
        print "Did not match, got: "+line
        continue
    db.add_char(m.groups()[0], m.groups()[1], m.groups()[2], m.groups()[3].split(","))
