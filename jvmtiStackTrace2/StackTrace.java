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
import java.util.stream.Collectors;



// ###############################################MAIN################################################################


public class StackTrace {


	public final static int bbuffer_size = 3_200;
	public final static int ibuffer_size = bbuffer_size / 4;
	
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
		//setting sleep time
		setSleepTime(2000);
		
		
		ByteBuffer bb = ByteBuffer.allocateDirect(bbuffer_size);
		bb.order(ByteOrder.nativeOrder());
		intBuffer = bb.asIntBuffer();
		setBuffers(intBuffer);
		
		startStackTrace();


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
		List<Integer> methodsList = new ArrayList<Integer>();
		
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

            		methodsList.add(methId);

	            	int location = intBuffer.get(currentPosition++);
	            	currentPosition %= ibuffer_size;

	            	System.out.printf("Frame Trace: %s %d %n", getMethodName(methId), location);
	            }
		 }

		 showTopMethods(methodsList);

		 System.out.printf("Cleanup: %d %d %d%n", start, currentPosition, ibuffer_size);

		 while ( start != currentPosition )
		 {
			 System.out.printf("%d ", start);
			 intBuffer.put(start, 0);
			 start = ( start+1 ) % ibuffer_size;
		 }
		 System.out.println();
	}

	
	// ###################################################################################################################
	public void showTopMethods(List<Integer> methodIDs)
	{
		System.out.printf("*************************************************************************************** %n");
		Map<Integer, Integer> topIDs = new HashMap<>();
		methodIDs.stream().distinct().forEach((e)->{topIDs.put(e, 0);});
		methodIDs.stream().forEach((e)->{topIDs.put(e, topIDs.get(e)+1);});
		topIDs.entrySet().stream().sorted((e1, e2)->e2.getValue()-e1.getValue()).limit(3).forEach((e)->{
			System.out.printf("Method ID: %d ... Frequency: %d ... Percentage: %.2f%% ... Name: %s ...  %n", e.getKey(), topIDs.get(e.getKey()), (1.0*e.getValue()/methodIDs.size())*100 , getMethodName(e.getKey())  );});
		System.out.printf("*************************************************************************************** %n");
	}	
}
