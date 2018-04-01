import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
// ###################################################################################################################
public class StackTrace {
	public final int tthreads;
	public final int traceint;
	public final String threadname;
	public boolean terminated;
	
	public final static int bbuffer_size = 50000;
	public final static int ibuffer_size = bbuffer_size / 4;
	public List<Integer> methodsList = new ArrayList<Integer>();

	//getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	public native void startStackTrace();
	public native void setSleepTime(int sTime);
	public native void setStackTraceRunning(boolean x);
	public native void setThreadList(Thread threadList[]);
	public native String getMethodName(int methodID);
	public native void setBuffers(IntBuffer buff); //setting the buffers

	private IntBuffer intBuffer;
	private int currentPosition = 0;
	
	// ###################################################################################################################
	public StackTrace(int tthreads, int traceint, String threadname)
	{
		this.tthreads = tthreads;
		this.traceint = traceint;
		this.threadname = threadname;
		this.terminated = false;
		List<Integer> methodsList = new ArrayList<Integer>();
	}
	// ###################################################################################################################
	//check if buffer has data in it
	private boolean hasData()
	{
		return intBuffer.get(currentPosition)!=0;
	}
	// ###############################################MAIN################################################################
	public static void main(String[] args)  {

		args = new String[] {"200", "500", "Gauss", "Matrix", "10", "200"};
		//monitored threads, interval, threads name, class name, matrix size (n), number of computing threads
		try
		{
			//to measure the execution time of the code
			long startTime = System.currentTimeMillis();
			int tthreads = getIntArg(args[0], 1, 1000, "Invalid monitored threads, must be between 1 and total number of threads");
			int traceint = getIntArg(args[1], 1, 1000, "Invalid trace interval, must be between 1 and 1000 ms");
			String threadname = args[2];
			Class mainclass = Class.forName(args[3]);
			Method mainmeth = mainclass.getMethod("main", args.getClass());
			String copied[] = Arrays.copyOfRange(args, 4, args.length);
			mainmeth.invoke(null, (Object) copied); 
			StackTrace jniObject = new StackTrace(tthreads, traceint, threadname);
			
			jniObject.run();
			//to measure the execution time of the code and print it
			long stopTime = System.currentTimeMillis();
			System.out.println("############################### Java Agent MAIN(): Elapsed time was " + (stopTime - startTime) + " miliseconds. ###############################");
		} catch(ClassNotFoundException ex) {
			ex.printStackTrace(System.err);
		} catch(NoSuchMethodException ex) {
			ex.printStackTrace(System.err);
		} catch(IllegalAccessException ex) {
			ex.printStackTrace(System.err);
		} catch(Exception ex) {
			ex.printStackTrace(System.err);
		} 	
	}
	// ###################################################################################################################
	private static int getIntArg(String arg, int min, int max, String mesg) {
		try {
			int result = Integer.parseInt(arg);
			if ( result < min || result > max ) {
				System.err.println(mesg);
				System.exit(1);
			}
			return result;
		}
		catch ( NumberFormatException ex ) {
			System.err.println(String.format("Invalid integer input %s", arg));
			System.exit(1);
		}
		return -1;
	}
	
	// ###################################################################################################################
	public List<Thread> getThreadByName(String threadName) {
		List<Thread> collectedThreads = new ArrayList<Thread>();
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.getName().equals(threadName))
			{
				collectedThreads.add(t);
			}
		}
		
		return collectedThreads;
	}
	
	// ###################################################################################################################
	public void run() throws Exception
	{

		//setting the getting stakc trace to true
		setStackTraceRunning(true);

		// Get a List of all Threads name "Gauss"
		List<Thread> threadList = getThreadByName("Gauss");
		Collections.shuffle(threadList);
		Thread threadArr[] = new Thread[threadList.size()];
		threadList.toArray(threadArr);
		final Thread traced[] = Arrays.copyOf(threadArr, tthreads);
		
		//setting threads list
		setThreadList(traced);

		ByteBuffer bb = ByteBuffer.allocateDirect(bbuffer_size);
		bb.order(ByteOrder.nativeOrder());
		intBuffer = bb.asIntBuffer();
		setBuffers(intBuffer);

		startStackTrace();
		
		//special thread for showTopMethods
		new Thread(new Runnable()
		{
			public void run()
			{
				showTopMethods();
			}
		}).start();
		
		//special thread for checking dead threads, and terminating stack tracing when all threads are dead
		new Thread(new Runnable() {
			public void run() {
				awaitTermination(traced);
			}}).start();

		//threads and synch
		while(!terminated)
		{
			synchronized (intBuffer)
			{
				if( !hasData() )
				{ //true = empty, false = has data
					try
					{
					//System.out.println("wait");
					intBuffer.wait(5000);
					}
					catch(InterruptedException e)
					{
						//timeout
					}
				}
			}
			synchronized(methodsList) {
				consume();
			}
		}

	}
	// ###################################################################################################################
	void awaitTermination(Thread traced[]) {
		
		for(int i = 0; i < traced.length; i++)
		{
			//wait for all threaded process to finish
			try
			{
				traced[i].join();
				
            } catch (InterruptedException e)
			{
            }
		}
		terminated = true;
	}
	
	
	// ###################################################################################################################
	//getting next element and updating current position 
	public int nextBufferElement()
	{
		int element = intBuffer.get(currentPosition);
		currentPosition = (currentPosition + 1) % ibuffer_size;
		return element;
	}

	// ###################################################################################################################
	//consume method
	public void consume() throws InterruptedException
	{
		//System.out.printf("%n");
		//printing what is in buffer
		int start = currentPosition;
		int tagS = nextBufferElement();
		int data_size = nextBufferElement();
		int thread_count = intBuffer.get(currentPosition++);
		//System.out.printf("Stack Trace: %d %d %d %n", tagS, data_size, thread_count);
		for(int thread = 0; thread<thread_count; thread++)
		{
			int tid = nextBufferElement();
			int state = nextBufferElement();
			int frame_count = nextBufferElement();
			//System.out.printf("Thread Trace: %d %d %d %d %n", tid, state, frame_count, thread_count);
			for (int frame = 0; frame<frame_count; frame++)
			{
				int methId = nextBufferElement();
				methodsList.add(methId);
				int location = nextBufferElement();
			}		 
		}
		//System.out.printf("Cleanup from %d to %d, Buffer size: %d %n%n", start, currentPosition, ibuffer_size);
		while ( start != currentPosition )
		{
			//System.out.printf("%d ", start);	
			intBuffer.put(start, 0);
			start = ( start+1 ) % ibuffer_size;
		}
		//System.out.println("Cleanup done");
	}


	// ###################################################################################################################
	//for testing purpose we list all threads to select from
	public void showAllThreads()
	{
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		while ( tg.getParent() != null )
		{
			tg = tg.getParent();
		}
		Thread t[] = new Thread[1024];
		int x = tg.enumerate(t, true);
		for (int i = 0; i < x; i++)
		{
			System.out.printf("%d : t_id: %d , t_name: %s %n", i, t[i].getId(), t[i].getName());
		}
	}

	// ###################################################################################################################
	//getting thread by ID
	public Thread getThread( final long id )
	{
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		while ( tg.getParent() != null )
		{
			tg = tg.getParent();
		}
		Thread t[] = new Thread[1024];
		int xt = tg.enumerate(t, true);

		for (int i = 0; i < xt; i++)
		{
			if( t[i].getId() == id)
			{
				System.out.printf("getting thread by ID: %d %d %s%n", i, t[i].getId(), t[i].getName());
				return t[i];
			}
		}
		System.out.printf("No thread found for ID: %d%n", id);
		return null;

	}

	// ###################################################################################################################
	//method to calculate % of methods freq.
	public void showTopMethods()
	{
		long startTime = System.currentTimeMillis();
		try
		{
			Thread.sleep(6000);
			List<Integer> oldList = this.methodsList;
			methodsList = new ArrayList<Integer>();
			final int countMap[]= new int[65537]; // Must be at least as big as ID_CACHE_SIZE in native part
			int methListSize = oldList.size();

			for (Integer element : oldList) {
				if ( element.intValue() >= 0 && element.intValue() < countMap.length ) {
					countMap[element.intValue()]++;
				}
			}
			List<Integer> topList = new ArrayList<Integer>();
			for(int i=0; i<countMap.length; i++) {
				if ( countMap[i] != 0 ) {
					topList.add(i);
				}
			}
			Collections.sort(topList, new Comparator<Integer>()
			{
				public int compare(Integer o1, Integer o2)
				{
					return countMap[o2]-countMap[o1];
				}
			});
			for(int mList=0; mList<5 && mList<topList.size();mList++)
			{
				int methId = topList.get(mList);
				String currMethodName = getMethodName(methId);
				int methFreq = countMap[methId];
				double percentMethod = (1.0*methFreq/topList.size())*100;
				System.out.printf("Method ID: %d ... Frequency: %d ... Percentage: %.2f%% ... Name: %s ...  %n", methId, methFreq, 
						percentMethod, currMethodName );
			}
			oldList.clear();
		} catch (Exception e)
		{
			e.printStackTrace();
			System.out.println("*** ERROR WHILE PRINTING CALCULATIONS ***");
		}
		//to measure the execution time of the code and print it
		long stopTime = System.currentTimeMillis();
		System.out.println("############################### Show Top Method: Elapsed time was " + (stopTime - startTime) + " miliseconds. ###############################");
	}	
}
