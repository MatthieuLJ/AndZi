#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import hanzidb
import cedict_parser

if len(sys.argv) != 3:
    print "not enough argument "+sys.argv[0]+" cedict_file database"
    sys.exit()
parser = cedict_parser.CedictParser(sys.argv[1])
db = hanzidb.HanziDB(sys.argv[2])
total_lines = parser.total_lines()
i = 0
for voc in parser:
    i = i + 1
    if i % 50 == 0:
        sys.stdout.write("progress : "+ str(parser.get_current_line())+ " / "+ str(total_lines)+ "\r")
    cw = db.add_cword(*voc[0:2])
    db.add_english(cw, *voc[2:3])
print ""
print "imported ",db.word_index," chinese words"
