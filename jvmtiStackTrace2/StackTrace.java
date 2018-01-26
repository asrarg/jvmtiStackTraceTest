import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;

public class StackTrace {

	//public native void setSTSleepTime(int x); //setting the sleep time for stack trace (how often to get ST)
	//public native void stackTraceSwitch(int x, boolean x); //switch fetching for stack traces on and off in addition to the sleep time

	public native void startStackTrace(); //getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	public native boolean detectBlackSwan(); //is there a black Swan event?
	public native String[] getTopMethods(); //getting top methods in stack trace
	public native String getCurrentThreadName(); //getting current thread name
	public native int getThreadCount();

	public native void setBuffers(ByteBuffer buff); //setting the buffers
	static ByteBuffer jbuff;


	//check if buffer has data in it (this is not correct! should be by the current position)
	private static boolean hasData(ByteBuffer j){
		int checks = 0;
		for (byte b : j) {
			if (b != 0) {
				checks++;
			}
		}
		return (checks == 0);
	}

	// ###############################################MAIN################################################################
	public static void main(String[] args) throws Exception {
		StackTrace jniObject = new StackTrace();

		//printing name of current thread
		System.out.println("Current thread name: " + jniObject.getCurrentThreadName());

		//printing top methods
		System.out.println("Print Top Methods");
		String[] methods = jniObject.getTopMethods();
		if (methods != null) {
			for (String m : methods) {
				System.out.println("> " + m);
			}
		} else {
			System.out.println("Got NULL from JNI.");
		}

		//******************* BUFFERS AND THREADS *******************

		jBuff = ByteBuffer.allocateDirect(10000);
		jniObject.setBuffers(jBuff);
		jniObject.startStackTrace()

		// Get the current number of live threads
		int threadCount = jniObject.getThreadCount();
		Thread[] threads = new Thread[threadCount];
		thread *arr[] = jniObject.startStackTrace();//switch this to java
		for(int i=0; i<arr.length; i++)
		{
			//arr[i] = 

		}

		//threads and synch
		while(true){
			synchronized (jBuff) {
				if( !hasData(jBuff) ){ //true = empty, false = has data
					jBuff.wait();
				}
			}
			consume(jBuff)
		}
		
		//consume method
		public void consume(ByteBuffer jBuff) throws InterruptedException
		{
			if ( !hasData(jb) ){
				return;
			}
			// print all Data from jb, 
		}







	}










}
