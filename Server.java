import java.io.*;
import java.lang.Thread.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;

class constants {
    public static final int A = 0;
    public static final int Z = 25;
    public static final int numLetters = 26;
}

class TransactionAbortException extends Exception {}
// this is intended to be caught
class TransactionUsageError extends Error {}
// this is intended to be fatal
class InvalidTransactionError extends Error {}
// bad input; will have to skip this transaction

// TO DO: you are not permitted to modify class Account
//
class Account {
    private int value = 0;
    private Thread writer = null;
    private HashSet<Thread> readers;

    public Account(int initialValue) {
        value = initialValue;
        readers = new HashSet<Thread>();
    }

    private void delay() {
        try {
            Thread.sleep(100);  // ms
        } catch(InterruptedException e) {}
            // Java requires you to catch that
    }

    public int peek() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer == self || readers.contains(self)) {
                // should do all peeks before opening account
                // (but *can* peek while another thread has open)
                throw new TransactionUsageError();
            }
            return value;
        }
    }

    // TO DO: the sequential version does not call this method,
    // but the parallel version will need to.
    //
    public void verify(int expectedValue)
        throws TransactionAbortException {
        delay();
        synchronized (this) {
            if (!readers.contains(Thread.currentThread())) {
                throw new TransactionUsageError();
            }
            if (value != expectedValue) {
                // somebody else modified value since we used it;
                // will have to retry
                throw new TransactionAbortException();
            }
        }
    }

    public void update(int newValue) {
        delay();
        synchronized (this) {
            if (writer != Thread.currentThread()) {
                throw new TransactionUsageError();
            }
            value = newValue;
        }
    }

    // TO DO: the sequential version does not open anything for reading
    // (verifying), but the parallel version will need to.
    //
    public void open(boolean forWriting)
        throws TransactionAbortException {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (forWriting) {
                if (writer == self) {
                    throw new TransactionUsageError();
                }
                int numReaders = readers.size();
                if (writer != null || numReaders > 1
                        || (numReaders == 1 && !readers.contains(self))) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                writer = self;
            } else {
                if (readers.contains(self) || (writer == self)) {
                    throw new TransactionUsageError();
                }
                if (writer != null) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                readers.add(Thread.currentThread());
            }
        }
    }

    public void close() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer != self && !readers.contains(self)) {
                throw new TransactionUsageError();
            }
            if (writer == self) writer = null;
            if (readers.contains(self)) readers.remove(self);
        }
    }

    // print value in wide output field
    public void print() {
        System.out.format("%11d", new Integer(value));
    }

    // print value % numLetters (indirection value) in 2 columns
    public void printMod() {
        int val = value % constants.numLetters;
        if (val < 10) System.out.print("0");
        System.out.print(val);
    }
}

class NCache
{
    private Account acc;
    private int reporting;
    private int initial;
    private boolean read;
    private boolean write;
    
    private boolean ISOPENED;
    public int ACCNUM;
    public char name;
    
    public NCache(Account ref, int num)
    {
        acc = ref;
        reporting = -1;
        initial = -1;
        read = false;
        write = false;
        ISOPENED = false;
        ACCNUM = num;
        name = (char) (num + 'A');
    }
    
    public void open() throws TransactionAbortException
    {
        //System.out.print(new Character((char) (i + 'A')) + ": ");
        //open if using read or write access... or both!
        if(read)
        {
            acc.open(false);
            //System.out.println("Account " + name + " opened for reading in Thread " + Thread.currentThread().getName());
        }
        if(write)
        {
            acc.open(true);
            //System.out.println("Account " + name + " opened for writing in Thread " + Thread.currentThread().getName());
        }  
        if(read || write)
        {
            
            ISOPENED = true;
        }
    }
    
    public void verify() throws TransactionAbortException
    {
        //verifies that the initial value is in fact correct
        if(read && ISOPENED)
        {
            acc.verify(initial);
            //System.out.println("Account " + name + " verified for reading in Thread " + Thread.currentThread().getName());
        }
        
    }
    
    public void commit()
    {
        if(write && ISOPENED)
        {
            acc.update(reporting);
            //System.out.println("Account " + name + " updated to value " + reporting + " in Thread " + Thread.currentThread().getName());
        }
    }
    
    public void close()
    {
        if(ISOPENED)
        {
            acc.close();
            //System.out.println("Account " + name + " closed in Thread " + Thread.currentThread().getName());
        }
        read = false;
        write = false;
        ISOPENED = false;
    }
    
    public void setCurrentValue(int other)
    {
        write = true;
        
        reporting = other;
    }
    
    public int getCurrentValue()
    {
        /*
        
        */
        if(read == false && write == false)
        {
            // Never been opened before. What we're looking for is the initial value. Pull them
            reporting = acc.peek();
            initial = reporting;
            read = true;
            return initial;
        }
        
        //Otherwise we've already set the reporting value via this function or setCurrentValue
        return reporting;
    }  
}

class NWorker implements Runnable
{
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;
    
    private final NCache[] cachedAccounts;
    private final String transaction;
    
    public NWorker(Account[] allAccounts, String trans)
    {
        cachedAccounts = new NCache[allAccounts.length];
        for(int i = 0; i < allAccounts.length; i++)
        {
            cachedAccounts[i] = new NCache(allAccounts[i], i);
        }
        
        transaction = trans;
    }
    
    public void abort()
    {
        //System.out.println("Aborting Transaction " + transaction);
        
        for(NCache n : cachedAccounts)
        {
            n.close();
        }
    }
    
    public void run()
    {
        //TODO: Replace this with a random number.
        int i = 1;
        
        while(true)
        {
            try
            {
                doRun();
                return;
            }
            catch(TransactionAbortException e)
            {
                //System.out.println("Failure on transaction: " + transaction);
                try
                {
                    Thread.sleep(100*i);
                    i += 1;
                }
                catch(InterruptedException f)
                {
                    ;;;
                }
            }
        }
    }
    
    public void doRun() throws TransactionAbortException
    {
        String[] commands = transaction.split(";");
        
        for(String command : commands)
        {
            String[] words = command.trim().split("\\s");
            
            if(words.length < 3)
                throw new InvalidTransactionError();
            if(!words[1].equals("="))
                throw new InvalidTransactionError();
            if(words.length%2 == 0)
                throw new InvalidTransactionError();
            
            NCache lhs = parseCache(words[0]);
            
            int workingSum = 0;
            
            workingSum = parseValue(words[2]);
            
            for(int j = 3; j < words.length; j += 2)
            {
                if(words[j].equals("+"))
                {
                    workingSum += parseValue(words[j+1]);
                }
                else if(words[j].equals("-"))
                {
                    workingSum -= parseValue(words[j+1]);
                }
                else
                    throw new InvalidTransactionError();
            }
            
            lhs.setCurrentValue(workingSum);
        }
        
        //ALL TRANSACTIONS DONE
        
        //phase 1: open/Verify
        for(int i =0; i < cachedAccounts.length; i++)
        {
            //System.out.println("Opening: " + i);
            try
            {
                cachedAccounts[i].open();
                cachedAccounts[i].verify();
            }
            catch(TransactionAbortException e)
            {
                //System.out.println("ABORT OCCURED");
                this.abort();
                throw e;
            }
        }
        
        //phase 2: Commit/close
        for(int i = 0; i < cachedAccounts.length; i++)
        {
            //System.out.println("Commiting to: " + i);
            cachedAccounts[i].commit();
            cachedAccounts[i].close();
        }
        
        System.out.println("Commit: " + transaction);
        
    }
    
    private NCache parseCache(String name)
    {
        int accountNum = (int) (name.charAt(0)) - (int) 'A';
        
        if(accountNum < A || accountNum > Z)
            throw new InvalidTransactionError();
        
        NCache acc = cachedAccounts[accountNum];
        
        for(int i = 1; i < name.length(); i++)
        {
            if(name.charAt(i) != '*')
                throw new InvalidTransactionError();
            
            accountNum = acc.getCurrentValue() % numLetters;
            acc = cachedAccounts[accountNum];
        }
        
        return acc;
    }
    
    private int parseValue(String name)
    {
        int rval; 
        if(name.charAt(0) >= '0' && name.charAt(0) <= '9')
        {
            rval = new Integer(name).intValue();
        }
        else
        {
            NCache chk = parseCache(name);
            rval = chk.getCurrentValue();
        }
        
        return rval;
    }
    
}

/*
AccountCache:
    - Primary method for caching account values 
    - Records the initial value (requires read access)
    - Records the working value (requires either read or write access)
    - Records read/write accesses for this transaction

*/
class AccountCache
{
    public int init_val;
    public int working_val;
    public boolean read_access;
    public boolean write_access;
    
    public AccountCache(int gval)
    {
        init_val = gval;
        working_val = init_val;
        read_access = false;
        write_access = false;
    }   
}

/*
myWorker:
    - Modified version of the sequential Worker class
    - Implements Runnable interface (so it can be executed in a thread pool)
    - Contains same list of constants A, Z, numLetters as Worker
    - Maintains variables:
        - accounts, an array of Account that represents ALL accounts
        - transaction, the string containing the commands of the transaction
        - accountCache, an array of AccountCache ojects, matching 1-1 to the
          array accounts. This is null unless the account has been referenced
        - opened, an arraylist of all accounts we have actually opened 
        - head, a String used for debugging
*/
class myWorker implements Runnable
{
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;
    
    private final Account[] accounts;
    private final String transaction;
    private final AccountCache[] accountCache;
    
    private final ArrayList<Account> opened = new ArrayList();
           
    private final String head;
    
    /*
    Constructor:
        records the accounts, initializes the cache to null
    */
    public myWorker(Account[] allAccounts, String trans)
    {
        accounts = allAccounts;
        transaction = trans;
        accountCache = new AccountCache[accounts.length];
        head = /*(transaction.hashCode()%50) + */"blah" + ": ";
        for(AccountCache a : accountCache)
        {
            a = null;
        }
    }
    
    /*
    recordOpen:
        a is an account that we are opening in realtime. We want to note it in 
    case we have to abort. 
    */
    public void recordOpen(Account a)
    {
        for(Account b : opened)
        {
            if( b == a )
            {
                //already marked as open.
                return;
            }
        }
        
        opened.add(a);
        
    }

    /*
    doAbort:
    TODO: FIX THIS BROKEN ASS FUNCTION
        given a list of open accounts, close them, because we have to redo this
        whole transaction. 
    */
    public void doAbort(int lastIndex)
    {
        for(Account a : opened)
        {
            try
            {
                a.close();
            }
            catch(TransactionUsageError e)
            {
                System.out.println("THIS FUCKED UP");
            }
        }
        
    }
    
    /*
    run:
        Overridden from the Runnable interface. 
        Trys to run this transaction (doRun). If it succeeds, commit and return. 
        If it throws an abort exception, we will need to re-run it (after some
        ammount of time)
    */
    public void run()
    {
        while(true)
        {
            try
            {
                System.out.println(head + " begins: " + transaction);
                doRun();
                return;
            }
            catch (TransactionAbortException e)
            {
                //Sleep, and then retry this.
                System.out.println(head + " FAILED");
                try
                {
                    Thread.sleep(500);
                }
                catch(InterruptedException f){}
            }
        }
    }
    
    /*
    doRun:
        Primary method of computation for transactions
        Splits transaction to commands. 
        Executes commands in order:
            Grabs a new AccountCache to write to (left hand side)
            Grabs the value associated with each term on the right hand side and
                if thats an account, it opens a cache to read from. 
            Sums those values
            Assigns the working value in the write cache
        Opens Accounts appropriately and verifies input
        Writes Accounts and closes them. 
    */
    public void doRun() throws TransactionAbortException
    {
        String[] commands = transaction.split(";");
        
        for(int i = 0; i < commands.length; i++)
        {
            // execute each command in this transaction in order
            String[] words = commands[i].trim().split("\\s");
            
            // check command length
            if (words.length < 3)
                throw new InvalidTransactionError();
            
            // check assignment operation
            if(!words[1].equals("="))
                throw new InvalidTransactionError();
            
            // open left-hand account for writing
            AccountCache lhs = parseAccountCache(words[0]);
            lhs.write_access = true;
            
            int workingTotal = 0;
            
            workingTotal = parseValue(words[2]);
            
            System.out.println(head + " initial value: " + workingTotal);
            
            for(int j = 3;j < words.length; j += 2)
            {
                //j is the op, j+1 is the next number
                if(words[j].equals("+"))
                {
                    workingTotal += parseValue(words[j+1]);
                }
                else if(words[j].equals("-"))
                {
                    workingTotal -= parseValue(words[j+1]);
                }
                else
                    throw new InvalidTransactionError();
            }
            
            System.out.println(head + " final value: " + workingTotal);
            lhs.working_val = workingTotal;
        }
        
        //All transactions complete. Commit them in canonical order
        
        
        //Phase 1: Open/Verify accounts
        for(int i = 0; i < accounts.length; i++)
        {
            if(accountCache[i] != null)
            {
                if(accountCache[i].read_access)
                {
                    try
                    {
                        System.out.println(head + " Opening " + (A + i) + " for read");
                        accounts[i].open(false);
                        accounts[i].verify(accountCache[i].init_val);
                        recordOpen(accounts[i]);
                    }
                    catch(TransactionAbortException e)
                    {
                        //Uh Oh! We dun goofed! Close all open accounts and abort
                        this.doAbort(i);
                        throw new TransactionAbortException();
                    }
                }
                if(accountCache[i].write_access)
                {
                    try
                    {
                        System.out.println(head + " Opening " + (A + i) + " for write");
                        accounts[i].open(true);
                        recordOpen(accounts[i]);
                    }
                    catch(TransactionAbortException e)
                    {
                        this.doAbort(i);
                        throw new TransactionAbortException();
                    }
                }
            }
        }
        
        
        //Phase 2: Update/Close accounts
        for(int i = 0; i < accounts.length; i++)
        {
            if(accountCache[i] != null)
            {
                if(accountCache[i].write_access)
                {
                    //System.out.println(head + " Writing to " + (A + i));
                    accounts[i].update(accountCache[i].working_val);
                    
                }
                
                accounts[i].close();
            }
        }
        
        //System.out.println("commit: " + transaction);
        
        return;
    }
    
    /*
    setCacheVal:
        This is a cache we need to know the initial value from. 
        Find the cache in the array, set its initial value via peek
        Correct read_access, and return it. 
    */
    private void setCacheVal(AccountCache other)
    {
        for (int i = 0; i < accounts.length; i++)
        {
            if(accountCache[i] == other)
            {
                //  Found appropriate cache in array
                other.init_val = accounts[i].peek();
                other.working_val = other.init_val;
                other.read_access = true;
                return;
            }
        }
    }
    
    /*
    getCache:
        Trys to see if a cache already exists for this account. If one does not
        exist, it creates a new AccountCache object. 
    */
    private AccountCache getCache(Account other, int num)
    {
        //check the nullity of accountCache[num]. If its not null, return it. 
        // if it is null, create a new cache based on other. 
        
        if(accountCache[num] != null)
        {
            return accountCache[num];
        }
        
        AccountCache rval = new AccountCache(0);
        //Read access assumed in constructor
        
        //Reassign the value to the cache
        accountCache[num] = rval;
        
        return rval;
    }
    
    
    /*
    parseAccountCache:
        Analogous to parseAccount in the Worker class. 
        Converts account letter to an integer
        Creates a cache for the specified account. 
            If there is a * operator on it, opens it for reading, gets the value
            then gets the accountCache for that number
    */
    private AccountCache parseAccountCache(String name)
    {
        /*
            If this account already exists in cache, return the reference to the 
            existing cache. If not, create one. 
        */
        
        //Convert letter to int % 26
        int accountNum = (int) (name.charAt(0)) - (int) 'A';
        
        //Check Boundaries
        if(accountNum < A || accountNum > Z)
            throw new InvalidTransactionError();
        
        //Get account reference, 
        Account a = accounts[accountNum];
        AccountCache acct = getCache(a, accountNum);
        
        for(int i = 1; i < name.length(); i++)
        {
            if(name.charAt(i) != '*')
                throw new InvalidTransactionError();
            
            /* To dereference pointer, get the value a.val % 26
                this is done via a.peek()%26. 
            
                We're going to get it via acct.working_val
            */
            setCacheVal(acct);
            accountNum = acct.working_val % 26;
            a = accounts[accountNum];
            acct = getCache(a, accountNum);
        }
        
        return acct;
    }

    /*
    parseValue:
        name can be either a number or an account. 
        If its a number, just parse out the value
        If its an account, parse it out, 
    */
    private int parseValue(String name)
    {
        int rval;
        if(name.charAt(0) >= '0' && name.charAt(0) <= '9')
        {
            //If its just a number, return the number
            rval = new Integer(name).intValue();
        }
        else
        {
            //Else make sure the AccountCache is marked for reading, then return
            // its cached value
            AccountCache chk = parseAccountCache(name);
            if(!chk.read_access)
            {
                setCacheVal(chk);
            }
            rval = chk.working_val;
        }
        
        return rval;
    }
    
}


// TO DO: Worker is currently an ordinary class.
// You will need to movify it to make it a task,
// so it can be given to an Executor thread pool.
//
class Worker {
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;

    private Account[] accounts;
    private String transaction;

    // TO DO: The sequential version of Worker peeks at accounts
    // whenever it needs to get a value, and opens, updates, and closes
    // an account whenever it needs to set a value.  This won't work in
    // the parallel version.  Instead, you'll need to cache values
    // you've read and written, and then, after figuring out everything
    // you want to do, (1) open all accounts you need, for reading,
    // writing, or both, (2) verify all previously peeked-at values,
    // (3) perform all updates, and (4) close all opened accounts.

    public Worker(Account[] allAccounts, String trans) {
        accounts = allAccounts;
        transaction = trans;
    }
    
    // TO DO: parseAccount currently returns a reference to an account.
    // You probably want to change it to return a reference to an
    // account *cache* instead.
    //
    private Account parseAccount(String name) {
        int accountNum = (int) (name.charAt(0)) - (int) 'A';
        if (accountNum < A || accountNum > Z)
            throw new InvalidTransactionError();
        Account a = accounts[accountNum];
        for (int i = 1; i < name.length(); i++) {
            if (name.charAt(i) != '*')
                throw new InvalidTransactionError();
            accountNum = (accounts[accountNum].peek() % numLetters);
            a = accounts[accountNum];
        }
        return a;
    }

    private int parseAccountOrNum(String name) {
        int rtn;
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            rtn = new Integer(name).intValue();
        } else {
            rtn = parseAccount(name).peek();
        }
        return rtn;
    }

    public void run() {
        // tokenize transaction
        String[] commands = transaction.split(";");

        for (int i = 0; i < commands.length; i++) {
            String[] words = commands[i].trim().split("\\s");
            if (words.length < 3)
                throw new InvalidTransactionError();
            Account lhs = parseAccount(words[0]);
            if (!words[1].equals("="))
                throw new InvalidTransactionError();
            int rhs = parseAccountOrNum(words[2]);
            for (int j = 3; j < words.length; j+=2) {
                if (words[j].equals("+"))
                    rhs += parseAccountOrNum(words[j+1]);
                else if (words[j].equals("-"))
                    rhs -= parseAccountOrNum(words[j+1]);
                else
                    throw new InvalidTransactionError();
            }
            try {
                lhs.open(true);
            } catch (TransactionAbortException e) {
                // won't happen in sequential version
            }
            lhs.update(rhs);
            lhs.close();
        }
        System.out.println("commit: " + transaction);
    }
}

public class Server {
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;
    private static Account[] accounts;
    
    private static final ExecutorService exec = Executors.newFixedThreadPool(3);
    

    private static void dumpAccounts() {
        // output values:
        for (int i = A; i <= Z; i++) {
            System.out.print("    ");
            if (i < 10) System.out.print("0");
            System.out.print(i + " ");
            System.out.print(new Character((char) (i + 'A')) + ": ");
            accounts[i].print();
            System.out.print(" (");
            accounts[i].printMod();
            System.out.print(")\n");
        }
    }

    public static void main (String args[])
        throws IOException {
        accounts = new Account[numLetters];
        for (int i = A; i <= Z; i++) {
            accounts[i] = new Account(Z-i);
        }

        // read transactions from input file
        String line;
        BufferedReader input =
            new BufferedReader(new FileReader(args[0]));

// TO DO: you will need to create an Executor and then modify the
// following loop to feed tasks to the executor instead of running them
// directly.  Don't modify the initialization of accounts above, or the
// output at the end.

        /* SIMPLE SEQUENTIAL TEST CASE
        while ((line = input.readLine()) != null) {
            Worker w = new Worker(accounts, line);
            w.run();
        }
        
        while((line = input.readLine()) != null)
        {
            myWorker w = new myWorker(accounts, line);
            exec.execute(w);
        }
*/
        
        while((line = input.readLine()) != null)
        {
            NWorker w = new NWorker(accounts, line);
            exec.execute(w);
        }
         
        exec.shutdown();
        
        try{
            exec.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch(Exception e)
        {
            System.out.println("Term failed");
        }

        
        
        System.out.println("final values:");
        dumpAccounts();
        
    }
}