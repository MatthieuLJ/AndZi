#!/usr/bin/python

import sys
import re
import codecs
import string

class CedictParser:
    """A class that can go through a cedict file and iterate through the lines one by one"""

    def __init__(self, filename):
        try:
            self.file = codecs.open(filename, "r", "utf-8")
        except IOError:
            print "cannot open file ",filename
            sys.exit()
        pass

    def __iter__(self):
        return self

    def next(self):
        # match a line
        while True:
            m=re.match(r"(.+) .+ \[(.+)\] (.+)", self.file.next())
            if m is not None:
                break

        # extract the different parts
        chinese=[i for i in m.groups()[0]]
        pinyins=m.groups()[1].split(" ")
        pinyins=[str(i).lower() for i in pinyins]
        translations=m.groups()[2].split("/")[1:-1]
        translations=[str(i) for i in translations]

        return (chinese,pinyins,translations)
    
if __name__ == "__main__":
    if len(sys.argv) != 2:
        print "not enough argument (give me the name of the cedict file)"
        sys.exit()
    parser = CedictParser(sys.argv[1])
    for voc in parser:
        print voc
    
    
