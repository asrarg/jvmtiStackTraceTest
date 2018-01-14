#include<stdio.h>
#include<string.h>
#include<malloc.h>
#include<jni.h>
#include<jvmti.h>
#include "JNITest.h"

// >>>> GLOBAL VARIABLES <<<<<
// jvmti environment - initialized in the agent onload
jvmtiEnv *jvmti;

// #####################################################################################################################
// Checking jvmtiErrors and print them out.
void check_jvmti_error(jvmtiError error, char *str) {

	// check if it is an error
	if (error != JVMTI_ERROR_NONE) {

        // getting the name of the error (is equal to the enum name: https://harmony.apache.org/subcomponents/drlvm/doxygen/vmcore/html/jvmti__types_8h.html#07555e9941d09b4404723e38e5defe39)
		char* err_name = NULL;
		(*jvmti)->GetErrorName(jvmti, error, &err_name);

        // print the error + given message to the console
		printf("NATIVE: ERROR: JVMTI: %d(%s): %s\n", error,
				(err_name == NULL ? "Unknown" : err_name),
				(str == NULL ? "" : str));
	}
}

// #####################################################################################################################
// Sample method - prints a text to the console
JNIEXPORT void JNICALL Java_JNITest_greet(JNIEnv *env, jobject obj) {
      printf("NATIVE: Welcome to the world of JNI...\n");
      return;
}

// #####################################################################################################################
// Sample method - returns a constant string
JNIEXPORT jstring JNICALL Java_JNITest_getString(JNIEnv *env, jobject obj) {
      return (*env)->NewStringUTF(env, "MethodEnd");
}

// #####################################################################################################################
// Sample method - returning the current cpu time of the current thread
JNIEXPORT jlong JNICALL Java_JNITest_getCurrentThreadCpuTime(JNIEnv *env, jobject obj) {
    printf("NATIVE: Getting current thread CPU time from JVMTI...\n");

    jlong nanos = -1;
    jvmtiError error;

    // calling jvmti to obtain the current thread
    error =  (*jvmti)->GetCurrentThreadCpuTime(jvmti, &nanos);
    check_jvmti_error(error, "Error while getting current thread cpu time.");

    return nanos;
}

// #####################################################################################################################
// Sample method - returning the name of the current thread
JNIEXPORT jstring JNICALL Java_JNITest_getCurrentThreadName(JNIEnv *env, jobject obj) {
    printf("NATIVE: Getting current thread name...\n");

    jvmtiError error;

    // obtaining the current thread
    jthread currentThread;
    error = (*jvmti)->GetCurrentThread(jvmti, &currentThread);
    check_jvmti_error(error, "Error while getting current thread");

    // fetching the name of the current thread
    jvmtiThreadInfo threadInfo;
    error = (*jvmti)->GetThreadInfo(jvmti,currentThread,&threadInfo);
    check_jvmti_error(error, "Error while getting thread infos");

    // creating a new jstring based on the thread name and returning it
    char* threadName = threadInfo.name;
    return (*env)->NewStringUTF(env, threadName);
}

// #####################################################################################################################
// Sample method - print the top method on each thread/stack
JNIEXPORT void JNICALL Java_JNITest_printTopMethodOfThreads(JNIEnv *env, jobject obj) {
    printf("NATIVE: Printing top methods...\n");

    jvmtiStackInfo *stack_info;
    jint thread_count;

    jvmtiError err;

    // fetching all stack traces, but just the first frame of each thread
    err = (*jvmti)->GetAllStackTraces(jvmti, 1, &stack_info, &thread_count);
    if (err != JVMTI_ERROR_NONE) {
        check_jvmti_error(err, "Error while getting thread infos");
    } else {
        printf("NATIVE: Found %d threads\n", thread_count);

        // iterating through all threads
        for (int i = 0; i < thread_count; i++) {

            // extracting some variables
            jvmtiStackInfo *infop = &stack_info[i];
            jthread thread = infop->thread;
            jint state = infop->state;
            jvmtiFrameInfo *frames = infop->frame_buffer;

            // fetching the thread info of the thread
            jvmtiThreadInfo threadInfo;
            err = (*jvmti)->GetThreadInfo(jvmti, thread, &threadInfo);

            printf("NATIVE: > Thread name: %s\n", threadInfo.name);
            printf("NATIVE: |-- State: %d\n", state);

            if (infop->frame_count <= 0) {
                printf("NATIVE: |-- No method executed\n");
            } else {
                // this loop is not necessary in this example (breaking in the first iteration), just for showing that the frames could be iterated
                // looping over the frames and printing the method of each frame
                for (int n=0; n < infop->frame_count; n++) {
                    jmethodID topMethodId = frames[n].method;

                    // getting the method name based on the method id of the frame
                    char *methodName;
                    err = (*jvmti)->GetMethodName(jvmti, topMethodId, &methodName, NULL, NULL);
                    check_jvmti_error(err, "Error while getting method name");

                    printf("NATIVE: |-- Executing method: %s\n", methodName);
                    break;
                }
            }
        }

        /* this one Deallocate call frees all data allocated by GetAllStackTraces */
        err = (*jvmti)->Deallocate(jvmti, (unsigned char *)stack_info);
        check_jvmti_error(err, "Error while dealocating memory");
    }
}

// #####################################################################################################################
// Sample method - returns a cosntant string array
JNIEXPORT jobjectArray JNICALL Java_JNITest_getStringArray(JNIEnv *env, jobject obj) {
    printf("NATIVE: Returning string array...\n");

    char* days[]={"Sunday",
                    "Monday",
                    "Tuesday",
                    "Wednesday",
                    "Thursday",
                    "Friday",
                    "Saturday"};

    jobjectArray myArray = 0;
    int len = sizeof(days) / sizeof(days[0]);

    myArray = (*env)->NewObjectArray(env, len, (*env)->FindClass(env,"java/lang/String"), 0);

    for(int i=0; i<len; i++) {
        jstring dayAsString = (*env)->NewStringUTF(env, days[i]);
        (*env)->SetObjectArrayElement(env, myArray, i, dayAsString);
    }

    return myArray;
}

// #####################################################################################################################
// Sample method - returns a string array containing the top methods
JNIEXPORT jobjectArray JNICALL Java_JNITest_getTopMethods(JNIEnv *env, jobject obj) {
    printf("NATIVE: Returning top methods...\n");

    // NOTE - this method is more or less exactly the same as Java_JNITest_printTopMethodOfThreads (two above) - the diffrence is that the methods are not printed but returned as an array like in the Java_JNITest_getStringArray method (one above)
    // read the Java_JNITest_printTopMethodOfThreads for a more detailed explanation what's going on
    jvmtiStackInfo *stack_info;
    jint thread_count;

    jvmtiError err;

    jobjectArray myArray = NULL;

    // fetching all stack traces, but just the first frame of each thread
    err = (*jvmti)->GetAllStackTraces(jvmti, 1, &stack_info, &thread_count);
    if (err != JVMTI_ERROR_NONE) {
        check_jvmti_error(err, "Error while getting thread infos");
    } else {

        // creating array
        myArray = (*env)->NewObjectArray(env, thread_count, (*env)->FindClass(env,"java/lang/String"), 0);

        // iterating through all threads
        for (int i = 0; i < thread_count; i++) {
            // extracting some variables
            jvmtiStackInfo *infop = &stack_info[i];
            jvmtiFrameInfo *frames = infop->frame_buffer;

            if (infop->frame_count > 0) {
                jmethodID topMethodId = frames[0].method;

                // getting the method name based on the method id of the frame
                char *methodName;
                err = (*jvmti)->GetMethodName(jvmti, topMethodId, &methodName, NULL, NULL);
                check_jvmti_error(err, "Error while getting method name");

                jstring methodnameAsString = (*env)->NewStringUTF(env, methodName);
                (*env)->SetObjectArrayElement(env, myArray, i, methodnameAsString);
            } else {
                (*env)->SetObjectArrayElement(env, myArray, i, (*env)->NewStringUTF(env, "n/a"));
            }
        }

        /* this one Deallocate call frees all data allocated by GetAllStackTraces */
        err = (*jvmti)->Deallocate(jvmti, (unsigned char *)stack_info);
        check_jvmti_error(err, "Error while dealocating memory");
    }

    return myArray;
}

//
// #####################################################################################################################
// #####################################################################################################################
// #####################################################################################################################
// The method which is executed when the agent is initialized (e.g. agent library loaded)
//
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
	printf("NATIVE: Agent was loaded!\n");

    // load JVMTI interface and set it to a global variable in order each method can use it
    jvmtiError error;
    error = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1_2);
    check_jvmti_error(error, "Error while getting JVMTI..");

    // init capabilities
    jvmtiCapabilities capa;
    jvmtiError err = (*jvmti)->GetCapabilities(jvmti, &capa);

    // set required caps. if no error occurred
    if (err == JVMTI_ERROR_NONE) {

        // preset required capabilities
        // See: https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html
        // Many methods need certain capabilites which are described in the docs
        capa.can_get_current_thread_cpu_time = 1;
        capa.can_get_line_numbers = 1;
        // ....

        // add set (=1) capabilities
        err = (*jvmti)->AddCapabilities(jvmti, &capa);
        check_jvmti_error(err, "Error while setting capabilities.");
    } else {
        check_jvmti_error(err, "Error while getting capabilities.");
    }
 	return JNI_OK;
}
