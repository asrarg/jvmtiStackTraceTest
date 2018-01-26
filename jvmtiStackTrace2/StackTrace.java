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

	public native void setBuffers(ByteBuffer buff); //setting the buffers
	static ByteBuffer jbuff;


	static final Producer PRODUCER;
	static final EventToken WORK_EVENT_TOKEN;
	static {
		PRODUCER = createProducer();
		WORK_EVENT_TOKEN = createToken(WorkEvent.class);
		PRODUCER.register();
	}
	//Creating event token.
	public static EventToken createToken(Class <? extends InstantEvent> clazz) {
		try {
			return PRODUCER.addEvent(clazz);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	//Creating producer thread.
	private static Producer createProducer() {
		try {
			return new Producer("Producer:","Creating ...");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	//check if buffer has data in it
	private static boolean hasData(ByteBuffer j){
		int checks = 0;
		for (byte b : j) {
			if (b != 0) {
				checks++;
			}
		}
		return (checks == 0);
	}


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

		//stack trace is recevied as a lineared byte buffer
		//Buffer size
		//int BUFFER_SIZE = 100;
		//Allocates a ByteBuffer  with a size of a 100
		//ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

		//******************* BUFFERS AND THREADS *******************

		jBuff = ByteBuffer.allocateDirect(1000);
		jniObject.setBuffers(jBuff);
		jniObject.startStackTrace()

		//ThreadMXBean bean = ManagementFactory.getThreadMXBean();  //(((get it from native and send to here)))
		// Get the current number of live threads including both daemon and non-daemon threads.
		//int threadCount = bean.getThreadCount();
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
