#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import os
import re
import codecs
import string
import sqlite3

class char_db:
    """A class to manage the character database"""
    def create_database(self):
        self.connection = sqlite3.connect(self.filename)
        self.connection.execute("CREATE TABLE chars (char VARCHAR(16) NOT NULL PRIMARY KEY, \
                mode VARCHAR(8) NOT NULL, \
                comp1 VARCHAR(16), \
                comp2 VARCHAR(16), \
                comp3 VARCHAR(16), \
                comp4 VARCHAR(16), \
                comp5 VARCHAR(16), \
                comp6 VARCHAR(16));")
        self.connection.execute("CREATE INDEX chars_char ON chars(char);")
        self.connection.commit()

    def __init__(self, filename):
        self.filename = filename
        if (os.path.exists(filename)):
            os.remove(filename)
        self.create_database()

    def add_char(self, char, mode, comps):
        query="INSERT INTO chars VALUES ('"+char+"', '"+mode+"'"
        for c in comps:
            query += ",'"+c+"'"
        for i in range(6-len(comps)):
            query += ",''"
        query += ");"
        self.connection.execute(query)
        self.connection.commit()

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

db=char_db(sys.argv[2])
while True:
    try:
        m = re.match(r"(.+):(.+)\((.+)\)", char_data_file.next())
    except StopIteration:
        sys.exit()

    current_line = current_line + 1
    if (current_line % 10 == 0):
        sys.stdout.write("Processing line "+str(current_line)+" out of "+str(total_num_lines)+"\r")
        sys.stdout.flush()

    if m is None:
        print "Did not match"
        continue
    db.add_char(m.groups()[0], m.groups()[1], m.groups()[2].split(","))
