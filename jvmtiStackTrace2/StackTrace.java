import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class StackTrace {

	//public native void setSTSleepTime(int x); //setting the sleep time for stack trace (how often to get ST)
	//public native void stackTraceSwitch(int x, boolean x); //switch fetching for stack traces on and off in addition to the sleep time

	public native void startStackTrace(); //getting stack trace as a lineared byte buffer every x ms (y is for switching fetching for stacktrace on or off)
	public native boolean detectBlackSwan(); //is there a black Swan event?
	public native String[] getTopMethods(); //getting top methods in stack trace
	public native String getCurrentThreadName(); //getting current thread name
	public native int getThreadCount();

	public native void setBuffers(IntBuffer buff); //setting the buffers
	
	private IntBuffer intBuffer;

	private int currentPosition;

	//check if buffer has data in it (this is not correct! should be by the current position)
	private boolean hasData(){
		return intBuffer.get(currentPosition)==0;
	}

	// ###############################################MAIN################################################################
	public static void main(String[] args) throws Exception {
		StackTrace jniObject = new StackTrace();
		jniObject.run();
	}
	
	public void run() throws Exception {
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

		intBuffer = ByteBuffer.allocateDirect(10000).asIntBuffer();
		setBuffers(intBuffer);
		startStackTrace();

		// Get the current number of live threads
		int threadCount = getThreadCount();
		/*Thread[] threads = new Thread[threadCount];
		thread arr[] = startStackTrace();//switch this to java
		for(int i=0; i<arr.length; i++)
		{
			//arr[i] = 

		}*/

		//threads and synch
		while(true){
			synchronized (intBuffer) {
				if( !hasData() ){ //true = empty, false = has data
					intBuffer.wait();
				}
			}
			consume();
			break;
		}
		
		//consume method
	}
	
	public void consume() throws InterruptedException
	{
		if ( !hasData() ){
			return;
		}
		for(int i=0; i<200;i++) {
			System.out.printf("%d ", intBuffer.get(i));
		}
	}
}
