'''
PyKeyWords.py

Authors:
    William Schneider
    William Kelly

Purpose:
    To Stat files in a provided directory for the purpose of discovering java keyword
    usage. 

'''

import re
import os
import sys

argc = len(sys.argv)
argv = sys.argv

head = argv[1]

def countKeywords(subdir):
    '''
        Recursive function to count the keywords in a given directory:
            Changes directory into the argument directory,
            For each file in the directory:
                if its a directory, recurse and copy the stats
                if its a java file, stat the file and copy the stats
            Return to the calling level directory
            Print the stats that were found
            Return the stats for recursive calls.
    '''
    topdir = os.getcwd()
    os.chdir(subdir)
    curdir = os.getcwd()
    stats = {'bytes':0,'public':0,'private':0,'try':0,'catch':0}
    
    # Iterate over the files
    for filename in os.listdir(curdir):
        fullPath = os.path.join(curdir, filename)
        
        if os.path.isdir(fullPath):
            # Retrieve the stats from the subdir
            sub = countKeywords(fullPath)
            stats['bytes'] += sub['bytes']
            stats['public'] += sub['public']
            stats['private'] += sub['private']
            stats['try'] += sub['try']
            stats['catch'] += sub['catch']
            
        elif os.path.isfile(fullPath) and (fullPath.find(".java") != -1):
            # Retrieve the stats from the file
            sub = statFile(fullPath)
            stats['bytes'] += sub['bytes']
            stats['public'] += sub['public']
            stats['private'] += sub['private']
            stats['try'] += sub['try']
            stats['catch'] += sub['catch']

    # Return to the calling directory, print the results, and return them. 
    os.chdir(topdir)
    pPrint(curdir, stats)
    return stats
    
def statFile(fileName):
    '''
        Function to collect statistics on a provided .java file
            Records file size in bytes
            Opens the file for reading, pulls all the contents into a string
            Records all multiline comments using regular expressions (between /* and */)
            Records all single line comments using regular expressions (after // until \n)
            Concatenates those blocks into a single list

            Counts ALL instances of keywords (bounded) in the file. This include inside
                comment blocks
            Counts instances of keywords (bounded) in ONLY the comment blocks (by iterating
                over the list).
            Uncommented instances are then ALL-COMMENTS

            Return collected statistics. 
                
    '''
    stats = {'bytes':0,'public':0,'private':0,'try':0,'catch':0}
    fsize = os.stat(fileName).st_size
    ifile = open(fileName, mode='r')
    contents = ifile.read()


    # Parse all commented blocks
    allmulti = re.findall("/\*.*?\*/", contents, re.DOTALL)
    allsingle = re.findall("//.*", contents)
    allcomments = allmulti + allsingle
                           
    # Parse ALL instances of keywords
    allpub = len(re.findall(r"\bpublic\b", contents))
    allpri = len(re.findall(r"\bprivate\b", contents))
    alltry = len(re.findall(r"\btry\b", contents))
    allcat = len(re.findall(r"\bcatch\b", contents))
    mpub, mpri, mtry, mcat = 0 , 0 , 0 , 0

    # Parse instances of keywords in comment blocks
    for comment in allcomments:
        mpub += len(re.findall(r"\bpublic\b", comment))
        mpri += len(re.findall(r"\bprivate\b", comment))
        mtry += len(re.findall(r"\btry\b", comment))
        mcat += len(re.findall(r"\bcatch\b", comment))
        
    stats['bytes'] = fsize
    stats['public'] = allpub - mpub
    stats['private'] = allpri - mpri
    stats['try'] = alltry - mtry
    stats['catch'] = allcat - mcat
    
    return stats
    
    
def pPrint(dirname, tup):
    # Print the directory and the statistics in some meaningful formatted way
    
    dn = dirname.ljust(25)
    b  = "bytes " + str(tup['bytes']).rjust(8)
    pu = "public " + str(tup['public']).rjust(4)
    pr = "private " + str(tup['private']).rjust(4)
    tr = "try " + str(tup['try']).rjust(4)
    ca = "catch " + str(tup['catch']).rjust(4)
    
    print dn, b, pu, pr, tr, ca
    
    
countKeywords(head)


