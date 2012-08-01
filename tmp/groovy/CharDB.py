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

