import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



// ###############################################MAIN################################################################


public class StackTrace {


	public final static int bbuffer_size = 3_200;
	public final static int ibuffer_size = bbuffer_size / 4;
	public List<Integer> methodsList = new ArrayList<Integer>();
	
	//getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	public native void startStackTrace();
	//is there a black Swan event?
	//public native boolean detectBlackSwan(); 
	//getting top methods in stack trace
	public native String[] getTopMethods();
	//getting current thread name
	public native String getCurrentThreadName(); 
	public native void setSleepTime(int sTime);
	public native void setStackTraceRunning(boolean x);
	public native void setThreadList(Thread threadList[]);
	public native String getMethodName(int methodID);
	public native void setBuffers(IntBuffer buff); //setting the buffers
	
	private IntBuffer intBuffer;
	private int currentPosition = 0;
	
	// ###############################################MAIN################################################################

	//check if buffer has data in it (this is not correct! should be by the current position)
	private boolean hasData()
	{
		return intBuffer.get(currentPosition)!=0;
	}
	
	// ###############################################MAIN################################################################
	public static void main(String[] args) throws Exception {
		StackTrace jniObject = new StackTrace();
		jniObject.run();
	}
	
	// ###############################################MAIN################################################################
	public void run() throws Exception
	{
		
		//setting the getting stakc trace to true
		setStackTraceRunning(true);
		
		//printing name of current thread
		System.out.println("Current thread name: " + getCurrentThreadName());

		//printing top methods
		System.out.println("Print Top Methods");
		String[] methods = getTopMethods();
		if (methods != null)
		{
			for (String m : methods)
			{
				System.out.println("> " + m);
			}
		} else {
			System.out.println("Got NULL from JNI.");
		}

		//******************* BUFFERS AND THREADS *******************
		
		
		showAllThreads();
		
		//asking user for sleepTime/interval AND threads list
		Scanner scanner = new Scanner(System.in);
		
		System.out.print("Enter the sampling interval in milliseconds, e.g. 2000: ");
		String sleepTimeInMilliSecondsString = scanner.nextLine();
		int sleepTimeInMilliSecondsInt = Integer.parseInt(sleepTimeInMilliSecondsString);
		
		if (sleepTimeInMilliSecondsInt < 2000)
		{
			do
			{
				System.out.print("sampling interval should be equal to or greater than 2000ms, please enter a valid interval: ");
				sleepTimeInMilliSecondsString = scanner.nextLine();
				sleepTimeInMilliSecondsInt = Integer.parseInt(sleepTimeInMilliSecondsString);
			}
			while(sleepTimeInMilliSecondsInt < 2000);
		}
		else
		{
			setSleepTime(sleepTimeInMilliSecondsInt);
		}
		
		System.out.print("Enter threads IDs seperated by commas: ");
		String threadsString = scanner.nextLine();
		List<String> threadsListString = Arrays.asList(threadsString.split("\\s*,\\s*"));

		//converting list string to long
		List<Long> threadsListLong = new ArrayList<Long>(threadsListString.size());
		for(String current:threadsListString)
		{
			threadsListLong.add(Long.parseLong(current));
		}
		
		int numberOfThreads = threadsListString.size();
		Thread threadsList[] = new Thread[numberOfThreads];
		
		
		int i=0;
		//getting threads by ID
		for(Long currentThreadID:threadsListLong)
		{
			Thread threadTemp = getThread(currentThreadID);
			System.out.println("temp thread: " +threadTemp);
			if(threadTemp != null)
			{
				threadsList[i] = threadTemp;
				System.out.println("adding to list thread with ID: " + threadsList[i].getId());
				i++;
			}
		}
		
		//setting threads list
		int threadslistlen = threadsList.length;
		System.out.println("Threads List LENGTH: " + threadslistlen);
		//for(int len=0; len < threadslistlen; len++)
		//{
			//System.out.println("List content["+len+"]: "+ threadsList[len].getName());
		//}
		setThreadList(threadsList);
		
		
		ByteBuffer bb = ByteBuffer.allocateDirect(bbuffer_size);
		bb.order(ByteOrder.nativeOrder());
		intBuffer = bb.asIntBuffer();
		setBuffers(intBuffer);
		
		startStackTrace();
		
		//special thread for showTopMethods
		new Thread(()->{showTopMethods();}).start();//runnable and in separate class, search for "schedualed executer service"

		//threads and synch
		while(true)
		{
			synchronized (intBuffer)
			{
				if( !hasData() )
				{ //true = empty, false = has data
					System.out.println("wait");
					intBuffer.wait();
				}
			}
			consume();
		}
	}
	// ###############################################MAIN################################################################
	//consume method
	public void consume() throws InterruptedException
	{
		if ( !hasData() )
		{
			return;
		}

		System.out.printf("%n");
        //printing what is in buffer
        int start = currentPosition;
        int tagS = intBuffer.get(currentPosition++);
		currentPosition %= ibuffer_size;
        int data_size = intBuffer.get(currentPosition++);
        currentPosition %= ibuffer_size;
        int thread_count = intBuffer.get(currentPosition++);
        System.out.printf("Stack Trace: %d %d %d %n", tagS, data_size, thread_count);
		
		//collecting all method Ids in list to calculate most frequent methods
		//do calculation once every 2 stack traces (something like every 1 minute or so)
		
        
        
        //List<Integer> methodsList = new ArrayList<Integer>(); // consider hash map
		//HashMap<Integer, String> methodIDsNames = new HashMap<Integer, String>();
		
		 for(int thread = 0; thread<thread_count; thread++)
		 {
	            int tid = intBuffer.get(currentPosition++);
	            currentPosition %= ibuffer_size;
	            int state = intBuffer.get(currentPosition++);
	            currentPosition %= ibuffer_size;
	            int frame_count = intBuffer.get(currentPosition++);
	            currentPosition %= ibuffer_size;
	            System.out.printf("Thread Trace: %d %d %d %n", tid, state, frame_count);


	            for (int frame = 0; frame<frame_count; frame++)
	            {

	            	int methId = intBuffer.get(currentPosition++);
	            	currentPosition %= ibuffer_size;

	    			synchronized(methodsList)
	    			{
	    				methodsList.add(methId);
	    			}
	    			String methodName = getMethodName(methId);
            		//methodIDsNames.put(methId, methodName);

	            	int location = intBuffer.get(currentPosition++);
	            	currentPosition %= ibuffer_size;

	            	System.out.printf("Frame Trace: %s %d %n", methodName, location);
	            }		 }


		 System.out.printf("Cleanup: %d %d %d%n", start, currentPosition, ibuffer_size);

		 while ( start != currentPosition )
		 {
			 System.out.printf("%d ", start);
			 intBuffer.put(start, 0);
			 start = ( start+1 ) % ibuffer_size;
		 }
		 
		 //showTopMethods(methodsList);

		 System.out.println();
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
		    System.out.printf("%d : t_id: %d , t_name: %s%n", i, t[i].getId(), t[i].getName());
		}
	}
	
	// ###################################################################################################################
	//getting thread by ID
	public Thread getThread( final long id )
	{
		/*
		Set<Thread> threadSet  = Thread.getAllStackTraces().keySet();
	    for ( Thread thread : threadSet )
	        if ( thread.getId( ) == id )
	        {
	        	System.out.printf(thread.getId() + ".. name: " + thread.getName());
	            return thread;
	        }
	    return null;
	    */
		 
		
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
			//System.out.printf("%d %d %s%n", i, t[i].getId(), t[i].getName());
		}
		System.out.printf("No thread found for ID: %d%n", id);
		return null;
		
	}
	
	// ###################################################################################################################
	//method to calculate % of methods freq.
	public void showTopMethods()
	{
		while(true)
		{
			try {
				Thread.sleep(6000);
				System.out.printf("*************************************************************************************** %n");
				Map<Integer, Integer> topIDs = new HashMap<>();
				int methListSize;
				synchronized(methodsList)
				{
					methodsList.stream().distinct().forEach((e)->{topIDs.put(e, 0);});
					methodsList.stream().forEach((e)->{topIDs.put(e, topIDs.get(e)+1);});
					methListSize = methodsList.size();
					methodsList.clear();
				}
				topIDs.entrySet().stream().sorted((e1, e2)->e2.getValue()-e1.getValue()).limit(3).forEach((e)->{
					System.out.printf("Method ID: %d ... Frequency: %d ... Percentage: %.2f%% ... Name: %s ...  %n", e.getKey(), topIDs.get(e.getKey()), 
							(1.0*e.getValue()/methListSize)*100, getMethodName(e.getKey())  );});
				System.out.printf("*************************************************************************************** %n");
			} catch (Exception e)
			{
				System.out.println("*** ERROR WHILE PRINTING CALCULATIONS ***");
			}
		}
	}	
}
