#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import os
import re
import codecs
import string
import sqlite3
import CharDB

def write_char_tree(character, db, output):
    data = db.get_char_data(character)
    if (data[1] == u'c'):
        output.write(data[0])
    else:
        output.write(data[0]+"="+data[1])
        if (len(data[3]) != 0):
            output.write("(")
            for i,c in enumerate(data[3]):
                if i>0:
                    output.write(",")
                write_char_tree(c, db, output)
            output.write(")")


if (len(sys.argv) != 4):
    print "not enough argument "+sys.argv[0]+" input_file charData.db output_file"
    sys.exit()

try:
    char_list=codecs.open(sys.argv[1], "r", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[1]
    sys.exit()

with open(sys.argv[1], "r") as f:
    for i, l in enumerate(f):
        pass
    total_num_lines=i+1
current_line=0

try:
    output=codecs.open(sys.argv[3], "w", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[3], " for writing"
    sys.exit()

db=CharDB.char_db(sys.argv[2])
while True:
    try:
        line = char_list.next()
    except StopIteration:
        char_list.close()
        output.flush()
        output.close()
        sys.exit()

    current_line += 1
    if (current_line % 10 == 0):
        sys.stdout.write("Processing line "+str(current_line)+" out of "+str(total_num_lines)+"\r")
        sys.stdout.flush()

    write_char_tree(line[0], db, output)
    output.write("\n")

