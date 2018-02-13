#include <windows.h>
#include <winbase.h>
#include <stdbool.h>
#include <stdint.h>
#include<stdio.h>
#include<string.h>
#include<malloc.h>
#include<jni.h>
#include<jvmti.h>
#include "StackTrace.h"

// >>>> GLOBAL VARIABLES <<<<<
// jvmti environment - initialized in the agent onload
static jvmtiEnv *jvmti;
static jobject g_dataReference = NULL;
static jbyte *g_dataBuffer = NULL;
static jlong g_dataCapacity;
static bool g_stackTraceRunning;
static int32_t g_sleepTimer;

//cache structure , define it, it contains all method ids and send
#define ID_CACHE_SIZE 65537

jmethodID g_idCache[ID_CACHE_SIZE];

#define INC currentOffset = (currentOffset + sOfInt) % (g_dataCapacity/4)

//######################################################################################################################
jmethodID getMethodId(int index)
{
    if ( index<0 || index >= ID_CACHE_SIZE ) {
        return NULL;
    }
    return g_idCache[index];
}

//######################################################################################################################
int addMethodId(jmethodID methId)
{
	int64_t location = ((int64_t)methId) * 17 % ID_CACHE_SIZE;
    int round = 0;
    while( g_idCache[location] != 0 && g_idCache[location] != methId) {
        location = (location+1) % ID_CACHE_SIZE;
        if ( round++ >= ID_CACHE_SIZE ) {
            return -1;
        }
    }
    g_idCache[location] = methId;
    return location;
}

// #####################################################################################################################
jstring getMethodName(JNIEnv *env, jint methodIdx)
{
	jvmtiError err;

	// getting the method name based on the method id of the frame
	jmethodID methodID = getMethodId(methodIdx);
	if (methodID == NULL ) {
		return NULL;
	}
	char *methodName=NULL;
	err = (*jvmti)->GetMethodName(jvmti, (jmethodID) methodID, &methodName, NULL, NULL);
	jstring result = (*env)->NewStringUTF(env, methodName);
	(*jvmti)->Deallocate(jvmti, methodName);
	return result;
}

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
JNIEXPORT void JNICALL Java_StackTrace_setSleepTime(JNIEnv *env, jobject obj, jint sleepTime)
{
	if ( sleepTime <= 0 ) {
		jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");;
		(*env)->ThrowNew(env, cls, "sleepTime must not be 0");
		return;
	}
	g_sleepTimer = sleepTime;
}
// #####################################################################################################################
JNIEXPORT jboolean JNICALL Java_StackTrace_getStackTraceRunning(JNIEnv *env, jobject obj)
{
	return g_stackTraceRunning;
}

// #####################################################################################################################
JNIEXPORT void JNICALL Java_StackTrace_setStackTraceRunning(JNIEnv *env, jobject obj, jboolean x)
{
	g_stackTraceRunning = x;
}

// #####################################################################################################################
JNIEXPORT jint JNICALL Java_StackTrace_getThreadCount(JNIEnv *env, jobject obj)
{

	jvmtiStackInfo *stack_info;
	jint thread_count;

	jvmtiError err;

	jobjectArray myArray = NULL;

	// fetching all stack traces, but just the first frame of each thread
	err = (*jvmti)->GetAllStackTraces(jvmti, 1, &stack_info, &thread_count);
	if (err != JVMTI_ERROR_NONE) {
		check_jvmti_error(err, "Error while getting thread infos");
	}
	err = (*jvmti)->Deallocate(jvmti, (unsigned char *)stack_info);
	check_jvmti_error(err, "Error while deallocating memory");
	return thread_count;
}

// #####################################################################################################################
JNIEXPORT void JNICALL Java_StackTrace_setBuffers(JNIEnv *env, jobject obj, jobject b1)
{
	if(b1 == NULL)
	{
		jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
		(*env)->ThrowNew(env, cls, "Buffer must not be Null");
		return;
	}
	//setting the global buffer.
	g_dataBuffer = (jbyte *) (*env)->GetDirectBufferAddress(env, b1);
	g_dataReference = (*env)->NewGlobalRef(env, b1);
	g_dataCapacity = (*env)->GetDirectBufferCapacity(env, b1);
	fprintf(stderr, "%d %d\n", g_dataBuffer, g_dataCapacity);
}

// #####################################################################################################################
JNIEXPORT void JNICALL Java_StackTrace_setValues(JNIEnv *env, jobject obj, jobject objValue, jint intValue)
{
	int xint = intValue;
	jobject xobj = objValue;

}

// #####################################################################################################################
//getting stack trace in a buffer and linearizing the buffer
void getStackTrace(jvmtiEnv* jvmti, JNIEnv* env, void* arg)
{
	//getting the ID field of the thread
	jclass threadClass = (*env)->FindClass(env, "java/lang/Thread");
	jfieldID tidFieldId = (*env)->GetFieldID(env, threadClass, "tid", "J");

	//current position of pointer
	jlong currentPos = 0;


	//g_stackTraceRunning = TRUE;

	//TODO extract the size to a local variable
	const int sOfInt = sizeof(jint);
	const int32_t tagD=0x00006ad1;
	const int32_t tagT=0xCafeBabe;
	const int32_t tagF=0xDeadBeef;



	while (g_stackTraceRunning)
	{
		fprintf(stderr, "Sleep Timer: %ld\n", g_sleepTimer);
		Sleep(g_sleepTimer);

		jvmtiStackInfo *stack_info;
		jint thread_count;

		jvmtiError err;
		err = (*jvmti)->GetAllStackTraces(jvmti, 15, &stack_info, &thread_count);
		if (err != JVMTI_ERROR_NONE) {
			check_jvmti_error(err, "Error while getting thread infos");
			continue;
		}

		// Number of int values following the initial state and size value
		int32_t data_size = 1;

		for (int i = 0; i < thread_count; i++) {
			//extracting some variables
			jvmtiStackInfo *infop = &stack_info[i];
			data_size += 3 + 3*infop->frame_count;
		} //end of threads loop


		jlong currentOffset = currentPos + sOfInt;
		fprintf(stderr,"%d %d\n", g_dataBuffer, currentOffset);
		//adding total data size to the beginning (2nd slot) of buffer
		memcpy(&g_dataBuffer[currentOffset], &data_size, sOfInt);
		currentOffset += sOfInt;
		//currentOffset %= (g_dataCapacity/4);
		//adding total thread count to the beginning (2nd slot) of buffer
		memcpy(&g_dataBuffer[currentOffset], &thread_count, sOfInt);
		currentOffset += sOfInt;//INC;
		//currentOffset %= (g_dataCapacity/4);

		for (int i = 0; i < thread_count; i++) {
			//extracting some variables
			jvmtiStackInfo *infop = &stack_info[i];
			jthread thread = infop->thread;
			jint tid = (*env)->GetLongField(env, thread, tidFieldId);
			jint state = infop->state;
			jint frame_count = infop->frame_count;
			jvmtiFrameInfo *frames = infop->frame_buffer;


			//thread info
			memcpy(&g_dataBuffer[currentOffset], &tid, sOfInt);
			currentOffset += sOfInt;
			//currentOffset %= (g_dataCapacity/4);
			memcpy(&g_dataBuffer[currentOffset], &state, sOfInt);
			currentOffset += sOfInt;//INC;
			//currentOffset %= (g_dataCapacity/4);
			memcpy(&g_dataBuffer[currentOffset], &frame_count, sOfInt);
			currentOffset += sOfInt;//INC;
			//currentOffset %= (g_dataCapacity/4);
			 //each frame has 2 elements (method and location)
			for(int j=0; j<frame_count;j++)
			{
				memcpy(&g_dataBuffer[currentOffset], &tagF, sOfInt);
				currentOffset += sOfInt;//INC;
				////currentOffset %= (g_dataCapacity/4);

				jint methIdx = addMethodId(frames[j].method);
				memcpy(&g_dataBuffer[currentOffset],&methIdx, sOfInt);
				currentOffset += sOfInt;
				//currentOffset %= (g_dataCapacity/4);

				memcpy(&g_dataBuffer[currentOffset], &frames[j].location, sOfInt); // it is the bytecode location
				currentOffset += sOfInt;//INC;
				//currentOffset %= (g_dataCapacity/4);
			} //end of frames loop


		} //end of threads loop

		/*
		//just printing data********************************
		int32_t *dataAlias = (int *)g_dataBuffer;
		for(int i=0; i<200;i++) {
			fprintf(stderr, "%08x ", dataAlias[i]);
			if ( i % 8 == 7 ) {
				fprintf(stderr, "\n");
			}
		}
		printf("x\n");
		//**************************************************		 */

		//adding state of buffer to the beginning (1st slot) of buffer
		memcpy(&g_dataBuffer[currentPos], &tagD, sOfInt);

		err = (*jvmti)->Deallocate(jvmti, (unsigned char *)stack_info);
		check_jvmti_error(err, "Error while deallocating memory");

		jint enterStatus = (*env)->MonitorEnter(env, g_dataReference);
		if(enterStatus != JNI_OK)
		{
			printf("Error: Monitor Enter\n");
			return;
		}

		currentPos = currentOffset;
		jclass refClass = (*env)->GetObjectClass(env, g_dataReference);
		jmethodID notifyId=(*env)->GetMethodID(env, refClass, "notifyAll", "()V");
		(*env)->CallVoidMethod(env, g_dataReference, notifyId);
		jint exitStatus = (*env)->MonitorExit(env, g_dataReference);
		(*env)->DeleteLocalRef(env, refClass);
		if(exitStatus != JNI_OK)
		{
			printf("Error: Monitor Exit\n");
			return;
		}
	}
}

// #####################################################################################################################

//starting stack trace
JNIEXPORT void JNICALL Java_StackTrace_startStackTrace(JNIEnv *env, jobject obj)
{
	// Check whether Thread already started g_stackTraceRunning
	jclass threadClass = (*env)->FindClass(env, "java/lang/Thread");

	if (threadClass == NULL) {
		printf("jclass error.");
	}

	jmethodID methodID = (*env)->GetMethodID(env, threadClass, "<init>", "()V"); // -> problem!
	if (methodID == NULL)
		printf("jmethodID error.");

	jthread threadObj = (*env)->NewObject(env, threadClass, methodID);
	if (obj == NULL)
		printf("jobject error.");

	// Set Daemon Thread //TODO check differecne between daemon and agent thread

// like thread.start() for native thread
	jvmtiError error = (*jvmti)->RunAgentThread(jvmti, threadObj, getStackTrace, NULL, JVMTI_THREAD_MAX_PRIORITY);

	check_jvmti_error(error, "Could not start thread");
	printf("Agent Thread started\n");
	return;

}

// #####################################################################################################################
JNIEXPORT jobjectArray JNICALL Java_StackTrace_stopStackTrace(JNIEnv *env, jobject obj)
{
	g_stackTraceRunning = false;
}



// #####################################################################################################################
// Sample method - returning the name of the current thread
JNIEXPORT jstring JNICALL Java_StackTrace_getCurrentThreadName(JNIEnv *env, jobject obj) {
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
JNIEXPORT void JNICALL Java_StackTrace_printTopMethodOfThreads(JNIEnv *env, jobject obj) {
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
		check_jvmti_error(err, "Error while deallocating memory");
	}
}

// #####################################################################################################################
// Sample method - returns a string array containing the top methods
JNIEXPORT jobjectArray JNICALL Java_StackTrace_getTopMethods(JNIEnv *env, jobject obj) {
	printf("NATIVE: Returning top methods...\n");

	// NOTE - this method is more or less exactly the same as Java_StackTrace_printTopMethodOfThreads (two above) - the diffrence is that the methods are not printed but returned as an array like in the Java_StackTrace_getStringArray method (one above)
	// read the Java_StackTrace_printTopMethodOfThreads for a more detailed explanation what's going on
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
		check_jvmti_error(err, "Error while deallocating memory");
	}

	return myArray;
}


// #####################################################################################################################
// #####################################################################################################################
// #####################################################################################################################
// The method which is executed when the agent is initialized (e.g. agent library loaded)
//
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
	printf("\nNATIVE: Agent was loaded!\n");

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
