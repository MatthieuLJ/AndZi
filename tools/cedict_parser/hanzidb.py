#!/usr/bin/python
# -*- coding: utf-8 -*-

import sqlite3
import sys
import os

class HanziDB:
    """A class that can manage an Hanzi SQLite database"""

    def create_database(self):
        self.connection = sqlite3.connect(self.filename)

        print "creating database"
        self.connection.execute("CREATE TABLE android_metadata (locale TEXT DEFAULT 'en_US');")
        self.connection.execute("INSERT INTO android_metadata VALUES ('en_US');")

        self.connection.execute("PRAGMA user_version=6;")

        self.connection.execute("CREATE TABLE char_pinyin ( \
                char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \
                hanzi VARCHAR(2) NOT NULL, \
                pinyin VARCHAR(10) NOT NULL, \
                UNIQUE(hanzi, pinyin));")
        self.connection.execute("CREATE INDEX char_pinyin_id ON char_pinyin(char_pinyin_id);")
        self.connection.execute("CREATE INDEX char_pinyin_hanzi ON char_pinyin(hanzi);")
        self.connection.execute("CREATE INDEX char_pinyin_pinyin ON char_pinyin(pinyin);")
        self.connection.execute("CREATE TABLE cword ( \
                cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \
                chinese VARCHAR(50) NOT NULL, \
                pinyin VARCHAR(150) NOT NULL, \
                length INTEGER DEFAULT 0, \
                UNIQUE(chinese,pinyin));")
        self.connection.execute("CREATE INDEX cword_id ON cword(cword_id);")
        self.connection.execute("CREATE INDEX cword_chinese ON cword(chinese);")
        self.connection.execute("CREATE INDEX cword_pinyin ON cword(pinyin);")
        self.connection.execute("CREATE TABLE cword_segment ( \
                cword_id INTEGER NOT NULL, \
                char_pinyin_id INTEGER NOT NULL, \
                pos INTEGER NOT NULL, \
                FOREIGN KEY (cword_id) REFERENCES cword(cword_id), \
                FOREIGN KEY (char_pinyin_id) REFERENCES char_pinyin(char_pinyin_id), \
                UNIQUE(cword_id, pos));")
        self.connection.execute("CREATE INDEX cword_segment_pos ON cword_segment(pos);")
        self.connection.execute("CREATE TABLE eword ( \
                eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \
                cword_id INTEGER NOT NULL, \
                translation VARCHAR(250) NOT NULL, \
                FOREIGN KEY (cword_id) REFERENCES cword(cword_id), \
                UNIQUE(cword_id, translation));")

        # Make it Android compatible
        self.connection.commit()

    def __init__(self, filename):
        self.filename = filename
        if not os.path.exists(filename):
            self.create_database()
        else:
            self.connection = sqlite3.connect(filename)
        self.pinyin_list={}
        self.pinyin_index=0
        self.word_list={}
        self.word_index=0
        self.english_list={}
        self.english_index=0
    
    def __del__(self):
        print "closing database"
        #self.connection.close()

    def add_pinyin(self, chinese, pinyin):
        if self.pinyin_list.has_key((chinese,pinyin)):
            return self.pinyin_list[(chinese, pinyin)]
        self.pinyin_index += 1
        self.connection.execute("INSERT INTO char_pinyin (char_pinyin_id, hanzi, pinyin) VALUES(?,?,?)", (self.pinyin_index, chinese, pinyin))
        self.connection.commit()
        self.pinyin_list[(chinese, pinyin)]=self.pinyin_index
        return self.pinyin_index

    def add_cword(self, chinese, pinyin):
        def add(x,y): return x+y
        complete_chinese=reduce(add, chinese)
        complete_pinyin=reduce(add, pinyin)

        if self.word_list.has_key((complete_chinese, complete_pinyin)):
            return self.word_list[(complete_chinese, complete_pinyin)]
        self.word_index += 1
        self.connection.execute("INSERT INTO cword (cword_id, chinese, pinyin, length) VALUES(?,?,?,?)", (self.word_index, complete_chinese, complete_pinyin, len(complete_chinese)))
        self.connection.commit()
        self.word_list[(complete_chinese, complete_pinyin)] = self.word_index
        for i in range(0, len(chinese)):
            self.connection.execute("INSERT INTO cword_segment (cword_id, char_pinyin_id, pos) VALUES(?,?,?)",
                    (self.word_index, self.add_pinyin(chinese[i], pinyin[i]), i))
            self.connection.commit()
        return self.word_index

    def add_english(self, cword, translations):
        for tr in translations:
            if self.english_list.has_key((cword, tr)):
                continue
            self.english_index += 1
            self.connection.execute("INSERT INTO eword (eng_id, cword_id, translation) VALUES(?,?,?)", (self.english_index, cword, tr))
            self.connection.commit()
            self.english_list[(cword, tr)]=self.english_index


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print "not enough argument (give me the name of the database)"
        sys.exit()
    db = HanziDB(sys.argv[1])
    cw=db.add_cword([u"一",u"下"], ["yi1", "xia4"])
    db.add_english(cw, ["right away", "now", "quick"])
