
# coding: utf-8

# In[59]:

import re
import os
import sys

argc = len(sys.argv)
argv = sys.argv

head = argv[1]

def countKeywords(subdir):
    topdir = os.getcwd()
    os.chdir(subdir)
    curdir = os.getcwd()
    stats = {'bytes':0,'public':0,'private':0,'try':0,'catch':0}
    # DO STUFF
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
            sub = statFile(fullPath)
            stats['bytes'] += sub['bytes']
            stats['public'] += sub['public']
            stats['private'] += sub['private']
            stats['try'] += sub['try']
            stats['catch'] += sub['catch']
        
    os.chdir(topdir)
    pPrint(curdir, stats)
    return stats
    
def statFile(fileName):
    #do stuff
    stats = {'bytes':0,'public':0,'private':0,'try':0,'catch':0}
    fsize = os.stat(fileName).st_size
    ifile = open(fileName, mode='r')
    contents = ifile.read()
    
    allmulti = re.findall("/\*.*?\*/", contents, re.DOTALL)
    allsingle = re.findall("//.*", contents)
    allcomments = allmulti + allsingle
                           
    
    allpub = len(re.findall("public", contents))
    allpri = len(re.findall("private", contents))
    alltry = len(re.findall("try", contents))
    allcat = len(re.findall("catch", contents))
    mpub, mpri, mtry, mcat = 0 , 0 , 0 , 0
    
    for comment in allcomments:
        mpub += len(re.findall("public", comment))
        mpri += len(re.findall("private", comment))
        mtry += len(re.findall("try", comment))
        mcat += len(re.findall("catch", comment))
        
    stats['bytes'] = fsize
    stats['public'] = allpub - mpub
    stats['private'] = allpri - mpri
    stats['try'] = alltry - mtry
    stats['catch'] = allcat - mcat
    
    return stats
    
    
def pPrint(dirname, tup):
    #do stuff
    dn = dirname.ljust(25)
    b  = "bytes " + str(tup['bytes']).rjust(8)
    pu = "public " + str(tup['public']).rjust(4)
    pr = "private " + str(tup['private']).rjust(4)
    tr = "try " + str(tup['try']).rjust(4)
    ca = "catch " + str(tup['catch']).rjust(4)
    
    print dn, b, pu, pr, tr, ca
    
    
countKeywords(head)


# In[58]:




# In[49]:




# In[46]:




# In[ ]:



