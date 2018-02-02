import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import java.io.*;
import java.text.*;
import java.util.*;

public class StackTrace {

	//public native void setSTSleepTime(int x); //setting the sleep time for stack trace (how often to get ST)
	//public native void stackTraceSwitch(int x, boolean x); //switch fetching for stack traces on and off in addition to the sleep time

	public native void startStackTrace(); //getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	//public native boolean detectBlackSwan(); //is there a black Swan event?
	public native String[] getTopMethods(); //getting top methods in stack trace
	public native String getCurrentThreadName(); //getting current thread name
	public native int getThreadCount();
	public native void setSleepTime(int sTime);
	public native void setValues(String x ,int y);
	public native void setStackTraceRunning(boolean x);

	public native void setBuffers(IntBuffer buff); //setting the buffers
	
	private IntBuffer intBuffer;

	private int currentPosition;

	//check if buffer has data in it (this is not correct! should be by the current position)
	private boolean hasData(){
		return intBuffer.get(currentPosition)!=0;
	}
	
	// ###############################################MAIN################################################################
	//methods to log in a file
	protected static String defaultLogFile = "javaLogFile.txt";

	public static void write(String s) {
		write(defaultLogFile, s);
	}

	public static void write(String f, String s) {
		TimeZone timezoneGermany = TimeZone.getTimeZone("CET");
		Date now = new Date();
		DateFormat dformat = new SimpleDateFormat ("yyyy.mm.dd hh:mm:ss ");
		dformat.setTimeZone(timezoneGermany);
		String currentTime = dformat.format(now);

		try {
			FileWriter aWriter = new FileWriter(f, true);
			aWriter.write(currentTime + " " + s + "\n");
			aWriter.flush();
			aWriter.close();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		System.out.println(currentTime + " " + s + "\n");
		
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
		
		
		ByteBuffer bb = ByteBuffer.allocateDirect(3_200);
		bb.order(ByteOrder.nativeOrder());
		intBuffer = bb.asIntBuffer();
		setBuffers(intBuffer);
		
		startStackTrace();

		// Get the current number of live threads
		int threadCount = getThreadCount();
		

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
		for(int i=0; i<800;i++) {
			System.out.printf("%08x ", intBuffer.get(i));
			if ( i % 16 == 15 ) {
				System.out.printf("%n");
			}
		}
		System.out.printf("%n");
		int dataSize = intBuffer.get(currentPosition+1)+2;
		for(int i=0; i<dataSize; i++) {
			intBuffer.put(currentPosition++, 0);
	    }
		
		
		
	}
}
