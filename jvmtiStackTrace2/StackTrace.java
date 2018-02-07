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

public class StackTrace {

	//public native void setSTSleepTime(int x); //setting the sleep time for stack trace (how often to get ST)
	//public native void stackTraceSwitch(int x, boolean x); //switch fetching for stack traces on and off in addition to the sleep time
	public final static int bbuffer_size = 3_200;
	public final static int ibuffer_size = bbuffer_size / 4;
	
	public native void startStackTrace(); //getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	//public native boolean detectBlackSwan(); //is there a black Swan event?
	public native String[] getTopMethods(); //getting top methods in stack trace
	public native String getCurrentThreadName(); //getting current thread name
	public native int getThreadCount();
	public native void setSleepTime(int sTime);
	public native void setValues(String x ,int y);
	public native void setStackTraceRunning(boolean x);
	public native String getMethodName(long methodID);

	public native void setBuffers(IntBuffer buff); //setting the buffers
	
	private IntBuffer intBuffer;

	private int currentPosition = 0;

	//check if buffer has data in it (this is not correct! should be by the current position)
	private boolean hasData(){
		return intBuffer.get(currentPosition)!=0;
	}
	

	// ###############################################MAIN################################################################
	public static void main(String[] args) throws Exception {
		StackTrace jniObject = new StackTrace();
		jniObject.run();
	}
	
	
	// ###############################################MAIN################################################################
	
	public void run() throws Exception {
		
		//setting the getting stakc trace to true
		setStackTraceRunning(true);
		
		//printing name of current thread
		System.out.println("Current thread name: " + getCurrentThreadName());

		//printing top methods
		System.out.println("Print Top Methods");
		String[] methods = getTopMethods();
		if (methods != null) {
			for (String m : methods) {
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

		// Get the current number of live threads
		//int threadCount = getThreadCount();

		

		//threads and synch
		while(true){
			synchronized (intBuffer) {
				if( !hasData() ){ //true = empty, false = has data
					System.out.println("wait");
					intBuffer.wait();
				}
			}
			consume();
			
		}
		
	}
	
	//consume method
	public void consume() throws InterruptedException
	{
		if ( !hasData() ){
			return;
		}
		//change this loop to another method 
		/*
		for(int i=0; i<ibuffer_size;i++) {
			System.out.printf("%08x ", intBuffer.get(i));
			if ( i % 16 == 15 ) {
				System.out.printf("%n");
			}
		}*/
		System.out.printf("%n");
		//print here what u get n buffer
		//u should see what data current position here has
		int start = currentPosition;
		int tagS = intBuffer.get(currentPosition++);
		int data_size = intBuffer.get(currentPosition++);
		int thread_count = intBuffer.get(currentPosition++);
		System.out.printf("Stack Trace: %d %d %d %n", tagS, data_size, thread_count);

		for(int thread = 0; thread<thread_count; thread++) {
			int tid = intBuffer.get(currentPosition++);
			int state = intBuffer.get(currentPosition++);
			int frame_count = intBuffer.get(currentPosition++);
			System.out.printf("Thread Trace: %d %d %d %n", tid, state, frame_count);
			for (int frame = 0; frame<frame_count; frame++) {
				int tagF = intBuffer.get(currentPosition++);
				
				int methId = intBuffer.get(currentPosition++);
				
				String methdName = getMethodName(methId);
				
				int location = intBuffer.get(currentPosition++);
				
				System.out.printf("Frame Trace: %d %d %d %n", tagF, methdName, location);
			}
		}

		while ( start != currentPosition ) {
			intBuffer.put(start, 0);
			start = ( start+1 ) % ibuffer_size;
		}


	}

	
}
