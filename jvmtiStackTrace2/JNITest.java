import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JNITest {

	// Not needed because we load the libary as an agent library using the jvm argument:
	// -agentlib:jniTestLib
	// static {
	// System.load("C:\\_Dev\\JNI\\JniTest\\jniTest.dll");
	// }

	// Native methods
	public native void greet();

	public native String getString();

	public native long getCurrentThreadCpuTime();

	public native String getCurrentThreadName();

	public native void printTopMethodOfThreads();

	public native String[] getStringArray();

	public native String[] getTopMethods();

	// variables
	public static double x = 0;

	public static ExecutorService executorService;

	public static void main(String[] args) throws Exception {
		JNITest jniObject = new JNITest();
		jniObject.greet();

		System.out.println("From native: " + jniObject.getString());

		jniObject.printCurrentCpuTime();

		System.out.println("Current thread name is: " + jniObject.getCurrentThreadName());

		jniObject.printTopMethodOfThreads();

		// spawing some threads
		executorService = Executors.newFixedThreadPool(10);
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				System.out.println("A new thread??");
			}
		});

		jniObject.printTopMethodOfThreads();

		jniObject.printCurrentCpuTime();

		System.out.println("Print Strings");
		String[] strings = jniObject.getStringArray();
		for (String m : strings) {
			System.out.println("> " + m);
		}

		System.out.println("Print Top Methods");
		String[] methods = jniObject.getTopMethods();
		if (methods != null) {
			for (String m : methods) {
				System.out.println("> " + m);
			}
		} else {
			System.out.println("Got NULL from JNI.");
		}

		executorService.shutdown();
	}

	public void printCurrentCpuTime() {
		long cpuTime = getCurrentThreadCpuTime();
		System.out.println("Current thread cpu time is: " + cpuTime);
	}
}