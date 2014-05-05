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
    
    /* NCache: Construct NCache with a reference to an account and a number.
     * The number is an identifier for easier debugging.
     * All other values initialized to -1 or false.
     */
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
    
    /*
     * open: Opens the account for reading and/or writing ONLY IF THE ACCOUNT
     * NEEDS TO BE OPENED. If it is opened either way, ISOPENED becomes true.
     * This will throw an abort if there's a conflicting thread that has it
     * open.
     */
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
    
    /*
     * verify: Calls verify on the account with the recorded initial value,
     * only if the account is being read. Aborts if the verify fails.
     */
    public void verify() throws TransactionAbortException
    {
        //verifies that the initial value is in fact correct
        if(read && ISOPENED)
        {
            acc.verify(initial);
            //System.out.println("Account " + name + " verified for reading in Thread " + Thread.currentThread().getName());
        }
        
    }
    
    /*
     * commit: Updates values on open accounts being written to. Does nothing
     * if it's not writing to the account.
     */
    public void commit()
    {
        if(write && ISOPENED)
        {
            acc.update(reporting);
            //System.out.println("Account " + name + " updated to value " + reporting + " in Thread " + Thread.currentThread().getName());
        }
    }
    
    /*
     * close: Closes the account if it's opened, AND resets the read/write and
     * ISOPENED values back to false. That last bit is important.
     */
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
    
    /*
     * Sets the value in the cached account, and designates it as being written
     * to.
     */
    public void setCurrentValue(int other)
    {
        write = true;
        
        reporting = other;
    }
    
    /*
     * Returns current value cached, and designates as read (unless it's
     * already been written, in which case the original value needn't be read).
     */
    public int getCurrentValue()
    {
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
    
    /*
     * NWorker: Create a cache of the given accounts and save the transaction.
     */
    public NWorker(Account[] allAccounts, String trans)
    {
        cachedAccounts = new NCache[allAccounts.length];
        for(int i = 0; i < allAccounts.length; i++)
        {
            cachedAccounts[i] = new NCache(allAccounts[i], i);
        }
        
        transaction = trans;
    }
    
    /*
     * abort: Just close all accounts. The cache will do nothing if it's not
     * already open.
     */
    public void abort()
    {
        //System.out.println("Aborting Transaction " + transaction);
        
        for(NCache n : cachedAccounts)
        {
            n.close();
        }
    }
    
    /*
     * run: Execute doRun. In the case of an abort exception, put the thread
     * to sleep for an increasing amount of time.
     */
    public void run()
    {
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
    
    /*
     * doRun: Does basically everything: split transactions into commands,
     * process command logic with the cache, and finally verify and update
     * the values of the actual accounts, catching aborts.
     */
    public void doRun() throws TransactionAbortException
    {
        // FIRST: Split up the commands and parse the left and right sides.
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
        
        // Open and verify accounts, catching aborts. Note that, while the
        // functions are called on all cache accounts, accounts with no write
        // or read will be unaffected.
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
        
        // Commit and close all accounts. As before, the cache accounts will
        // do nothing unless necessary.
        for(int i = 0; i < cachedAccounts.length; i++)
        {
            //System.out.println("Commiting to: " + i);
            cachedAccounts[i].commit();
            cachedAccounts[i].close();
        }
        
        System.out.println("Commit: " + transaction);
        
    }
    
    /*
     * parseCache: This returns the cached account identified by the letter
     * name of the account and however many reference operations are added.
     */
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
    
    /*
     * parseValue: This just returns the integer value of either a literal
     * integer in the string, or the account.
     * */
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


public class Server {
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;
    private static Account[] accounts;
    
    private static final ExecutorService exec = Executors.newFixedThreadPool(3);
    
    /*
     * dumpAccounts: Print all account names and values, including the value
     * modulo 26.
     */
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
        // For each transaction, make an NWorker and add it to the thread pool.
        while((line = input.readLine()) != null)
        {
            NWorker w = new NWorker(accounts, line);
            exec.execute(w);
        }
         
        // Stop accepting new workers when all have been added to the pool.
        exec.shutdown();
        
        // There's a thirty-second timeout for all the threads to finish.
        // This can be expanded if necessary.
        try{
            exec.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch(Exception e)
        {
            System.out.println("Term failed");
        }

        
        // And lastly, print all the values!
        System.out.println("final values:");
        dumpAccounts();
        
    }
}