#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import os
import re
import codecs
import string
import sqlite3
from cjklib import characterlookup

class groovy_db:
    """A class to manage the character database"""
    def create_database(self):
        self.connection = sqlite3.connect(self.filename)
        self.connection.execute("CREATE TABLE chars (char VARCHAR(16) NOT NULL PRIMARY KEY, \
                mode VARCHAR(8) NOT NULL, \
                flags VARCHAR(1),  \
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
        if not os.path.exists(filename):
            self.create_database()
        else:
            self.connection = sqlite3.connect(self.filename)

    def __del__(self):
        self.connection.close()

    def add_char(self, char, mode, flag, comps):
        query="INSERT INTO chars VALUES ('"+char+"', '"+mode+"', '"+flag+"'"
        for c in comps:
            query += ",'"+c+"'"
        for i in range(6-len(comps)):
            query += ",''"
        query += ");"
        self.connection.execute(query)
        self.connection.commit()

    def get_char_data(self, char):
        cur = self.connection.cursor()
        cur.execute("SELECT * FROM chars WHERE char=?", (char,))
        result = cur.fetchone()
        if result is None:
            return None
        components=[]
        for c in result[3:8]:
            if c is not u'':
                components.append(c)
        return result[0], result[1], result[2], tuple(components)

class wubi_db:
    """A class to manage the character database"""
    def create_database(self):
        self.connection = sqlite3.connect(self.filename)
        self.connection.execute("CREATE TABLE chars (char VARCHAR(16) NOT NULL PRIMARY KEY, \
            wubi VARCHAR(64));")
        self.connection.execute("CREATE INDEX chars_char ON chars(char);")
        self.connection.commit()

    def __init__(self, filename):
        self.filename = filename
        if not os.path.exists(filename):
            self.create_database()
        else:
            self.connection = sqlite3.connect(self.filename)

    def __del__(self):
        self.connection.close()

    def add_char(self, char, wubi):
        query="INSERT INTO chars VALUES ('"+char+"', '"+wubi+"');"
        try:
            self.connection.execute(query)
            self.connection.commit()
        except sqlite3.IntegrityError:
            pass


    def get_char_data(self, char):
        cur = self.connection.cursor()
        cur.execute("SELECT * FROM chars WHERE char=?", (char,))
        result = cur.fetchone()
        if result is None:
            return None
        return result[1]

class char_db:
    """A class to manage the character database"""
    def create_database(self):
        self.connection = sqlite3.connect(self.filename)
        self.connection.execute("CREATE TABLE chars (char VARCHAR(16) NOT NULL PRIMARY KEY, \
                wubi VARCHAR(64), \
                tree VARCHAR(256));")
        self.connection.execute("CREATE INDEX chars_char ON chars(char);")
        self.connection.commit()

    def __init__(self, filename):
        self.filename = filename
        if not os.path.exists(filename):
            self.create_database()
        else:
            self.connection = sqlite3.connect(self.filename)

    def __del__(self):
        self.connection.close()

    def add_char(self, char, wubi, tree):
        if wubi is None:
            wubi=get_wubi_from_cjk(char)
            if wubi is None:
                wubi=""
        query="INSERT INTO chars VALUES ('"+char+"', '"+wubi+"', '"+tree+"'"+");"
        self.connection.execute(query)
        self.connection.commit()

def get_char_tree(character, db):
    data = db.get_char_data(character)
    if data is None:
        print "Cannot find information about character ",character
        return None

    if data[1] == u'c':
        res=data[0]
    else:
        res=data[0]+"="+data[1]
        if len(data[3]):
            res+="("
            for i,c in enumerate(data[3]):
                if i>0:
                    res+=","
                subtree=get_char_tree(c, db)
                if subtree is not None:
                    res+=subtree
            res+=")"
    return res

def build_char_structure_db(filename):
    with open(filename, "r") as f:
        for i, l in enumerate(f):
            pass
        total_num_lines=i+1
    current_line=0

    if os.path.exists(filename+".db"):
        os.remove(filename+".db")

    gdb=groovy_db(sys.argv[1]+".db")
    while True:
        try:
            line = char_data_file.next()
        except StopIteration:
            break

        m = re.match(r"(.+):([^/]+)/*(.*)\((.*)\)", line)

        current_line += 1
        if current_line % 10 == 0:
            sys.stdout.write("Processing line (char_structure) "+str(current_line)+" out of "+str(total_num_lines)+"\r")
            sys.stdout.flush()

        if m is None:
            print "Did not match, got: "+line
            continue
        gdb.add_char(m.groups()[0], m.groups()[1], m.groups()[2], m.groups()[3].split(","))

    return gdb

def build_wubi_db(filename):
    with open(sys.argv[2], "r") as f:
        for i, l in enumerate(f):
            pass
        total_num_lines=i+1
    current_line=0.

    if (os.path.exists(sys.argv[2]+".db")):
        os.remove(sys.argv[2]+".db")

    wdb=wubi_db(sys.argv[2]+".db")
    while True:
        try:
            line = wubi_data_file.next()
        except StopIteration:
            break

        m = re.match(r"(.*)\t(.*)", line)

        current_line += 1
        if (current_line % 10 == 0):
            sys.stdout.write("Processing line (wubi data) "+str(current_line)+" out of "+str(total_num_lines)+"       \r")
            sys.stdout.flush()

        if m is None:
            print "Did not match, got: "+line
            continue
        wdb.add_char(m.groups()[1], m.groups()[0])

    return wdb

wubi_strokes = {
    "HZSZHZS" : "z",
    "HZSZHZP" : "z",
    "HZZZG" : "z",
    "HZSZH" : "z",
    "HZSZT" : "z",
    "HZSWH" : "z",
    "SZHZS" : "z",
    "HZSG" : "z",
    "HZWG" : "z",
    "SZWG" : "z",
    "HZZP" : "z",
    "HPWG" : "z",
    "HZZZ" : "z",
    "HXWG" : "z",
    "HZW" : "z",
    "WSG" : "z",
    "BXG" : "z",
    "SWH" : "z",
    "HZZ" : "z",
    "HZG" : "z",
    "HZP" : "z",
    "HZT" : "z",
    "SZH" : "z",
    "HZS" : "z",
    "SZT" : "z",
    "PZD" : "z",
    "PZH" : "z",
    "SZZ" : "z",
    "SWG" : "z",
    "HP" : "z",
    "WG" : "z",
    "SW" : "z",
    "HZ" : "z",
    "HG" : "z",
    "SZ" : "z",
    "ST" : "z",
    "PD" : "z",
    "PZ" : "z",
    "XG" : "n",
    "NG" : "n",
    "TN" : "n",
    "PG" : "p",
    "SP" : "p",
    "SG" : "s",
    "N" : "n",
    "T" : "n",
    "D" : "n",
    "H" : "h",
    "S" : "s",
    "P" : "p"
    }


def get_wubi_from_cjk(char):
    cjk = characterlookup.CharacterLookup('C')
    strokes = cjk.getStrokeOrderAbbrev(char)
    if strokes is not None:
        for s in wubi_strokes.keys():
            strokes = strokes.replace(s, wubi_strokes[s])
        strokes = strokes.replace("z-z", "z")
        strokes = strokes.replace("-", "")
        strokes = strokes.replace(" ", "")
        return strokes
    else:
        return None

if len(sys.argv) != 5:
    print "not enough argument "+sys.argv[0]+" charData(groovy) charData(wubi/ibus-t9) input_set database-output"
    sys.exit()

# Open the character decomposition data file
try:
    char_data_file=codecs.open(sys.argv[1], "r", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[1]
    sys.exit()

# Open the Wubi table file
try:
    wubi_data_file=codecs.open(sys.argv[2], "r", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[2]
    sys.exit()

# Process the character decomposition data into a temporary database
if sys.argv[1].endswith(".db"):
    gdb=groovy_db(sys.argv[1])
else:
    gdb=build_char_structure_db(sys.argv[1])

# Process the Wubi table into a temporary database
if sys.argv[2].endswith(".db"):
    wdb=wubi_db(sys.argv[2])
else:
    wdb=build_wubi_db(sys.argv[2])

try:
    char_list=codecs.open(sys.argv[3], "r", "utf-8")
except IOError:
    print "cannot open file ", sys.argv[3]
    sys.exit()

with open(sys.argv[3], "r") as f:
    for i, l in enumerate(f):
        pass
    total_num_lines=i+1
current_line=0

try:
    output=char_db(sys.argv[4])
except IOError:
    print "cannot open file ", sys.argv[4], " for writing"
    sys.exit()

while True:
    try:
        line = char_list.next()
    except StopIteration:
        char_list.close()
        sys.exit()

    current_line += 1
    if (current_line % 10 == 0):
        sys.stdout.write("Processing line "+str(current_line)+" out of "+str(total_num_lines)+" for output\r")
        sys.stdout.flush()

    #sys.stderr.write("Processing character "+line[0]+"\n")
    output.add_char(line[0], wdb.get_char_data(line[0]), get_char_tree(line[0], gdb))

