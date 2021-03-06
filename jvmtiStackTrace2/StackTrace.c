#ifdef WINDOWS
#include <windows.h>
#include <winbase.h>
#else
#include <unistd.h>
#endif
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
static jint *g_dataBuffer = NULL;
static jlong g_dataCapacity;
static bool g_stackTraceRunning;
static int32_t g_sleepTimer;
static jint g_threadCount;
static jobject g_threadRef;
static jthread *g_threadList;
//cache structure , define it, it contains all method ids and send
#define ID_CACHE_SIZE 65537
static jmethodID g_idCache[ID_CACHE_SIZE];
#define SET_CURRENT_OFFSET(O, L) \
  do {\
      O = (O + 1) % (L);\
  } while ( 0 )
//######################################################################################################################
JNIEXPORT void JNICALL Java_StackTrace_setThreadList (JNIEnv *env, jobject obj, jobjectArray threadList)
{
	// Before freeing g_threadList, delete all global reference
	if ( g_threadList != NULL ) {
		for(int i=0; i<g_threadCount; i++) {
			if ( g_threadList[i] != NULL ) {
				(*env)->DeleteGlobalRef(env, g_threadList[i]);
				g_threadList[i] = NULL;
			}
		}
		free(g_threadList);
	}
	g_threadCount = 0;
	g_threadList = NULL;
	if ( threadList == NULL )
	{
		return;
	}
	g_threadCount = (*env)->GetArrayLength(env, threadList);
	g_threadList = malloc(g_threadCount*sizeof(jthread));
	for (int i=0; i<g_threadCount; i++)
	{
		g_threadList[i] = (*env)->NewGlobalRef(env, (*env)->GetObjectArrayElement(env, threadList, i));
		//fprintf(stderr, "Thread %d %p\n", i, g_threadList[i]);
	}
}
//######################################################################################################################
jmethodID getMethodId(int index)
{
	//this method returns the method ID at the specified location in the global cache
    if ( index<0 || index >= ID_CACHE_SIZE )
    {
        return NULL;
    }
    return g_idCache[index];
}
//######################################################################################################################
int addMethodId(jmethodID methId)
{
	//this method stores the passed methodID in the global cache, it checks if it is already in the cache, it adds it and returns the location
	int64_t location = ((int64_t)methId) * 17 % ID_CACHE_SIZE;
    int round = 0;
    while( g_idCache[location] != 0 && g_idCache[location] != methId)
    {
        location = (location+1) % ID_CACHE_SIZE;
        if ( round++ >= ID_CACHE_SIZE )
        {
            return -1;
        }
    }
    g_idCache[location] = methId;
    //printf("Added %p at location %d\n", methId, location);

#ifdef WINDOWS
    Sleep(500);
#else
    usleep(500);
#endif

    return location;
}
// #####################################################################################################################
JNIEXPORT jstring JNICALL Java_StackTrace_getMethodName (JNIEnv *env, jobject obj, jint intValue)
{
    jvmtiError err;

    // getting the method name based on the method id of the frame
    //getting the method ID from the global cache
    jmethodID methodID = getMethodId(intValue);
    if (methodID == NULL )
    {
        return NULL;
    }
    //then getting the name of the method using the id
    char *methodName=NULL;
    err = (*jvmti)->GetMethodName(jvmti, (jmethodID) methodID, &methodName, NULL, NULL);
    jstring result = (*env)->NewStringUTF(env, methodName);
    (*jvmti)->Deallocate(jvmti, methodName);
    return result;
}

// #####################################################################################################################
// Checking jvmtiErrors and print them out.
void check_jvmti_error_internal(const char *file, int line, jvmtiError error, char *str)
{
	// check if it is an error
	if (error != JVMTI_ERROR_NONE)
	{
		// getting the name of the error
		char* err_name = NULL;
		(*jvmti)->GetErrorName(jvmti, error, &err_name);

		// print the error + given message to the console
		printf("NATIVE: ERROR: JVMTI: %s->%d, %d(%s): %s\n", file, line, error,
				(err_name == NULL ? "Unknown" : err_name),
				(str == NULL ? "" : str));
	}
}
#define check_jvmti_error(a, b) check_jvmti_error_internal(__FILE__, __LINE__, a, b)
// #####################################################################################################################
JNIEXPORT void JNICALL Java_StackTrace_setSleepTime(JNIEnv *env, jobject obj, jint sleepTime)
{
	if ( sleepTime <= 0 )
	{
		jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
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
	if (err != JVMTI_ERROR_NONE)
	{
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
    g_dataBuffer = (jint *) (*env)->GetDirectBufferAddress(env, b1);
    g_dataReference = (*env)->NewGlobalRef(env, b1);
    g_dataCapacity = (*env)->GetDirectBufferCapacity(env, b1);
    //printf("\n");
    //fprintf(stderr, "Buffer initially: %p %ld\n", g_dataBuffer, g_dataCapacity);
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
	//TODO extract the size to a local variable
	const int32_t tagD=0x00006ad1;
	while (g_stackTraceRunning)
	{
		jvmtiPhase phase;
		//fprintf(stderr, "Sleep Timer: %d\n", g_sleepTimer);
#ifdef WINDOWS
		Sleep(g_sleepTimer);
#else
		usleep(g_sleepTimer*1000);
#endif
		if ( (*jvmti)->GetPhase(jvmti, &phase) == JVMTI_ERROR_NONE && phase == JVMTI_PHASE_DEAD  ) {
			printf("Terminating stack trace\n");
			break;
		}
		jvmtiStackInfo *stack_info;
		jint thread_count;
		jvmtiError err;
		//if the threads list is NOT NULL then use GetThreadListStackTraces
		//if is equal to NULL then use the rest
		if(g_threadList != NULL)
		{
			thread_count = g_threadCount;
			//fprintf(stderr, "GTLST Arg: %d %p %p", thread_count, g_threadList, g_threadList[0]);
			err = (*jvmti)->GetThreadListStackTraces(jvmti, thread_count, g_threadList, 15, &stack_info);
		}
		else
		{
			err = (*jvmti)->GetAllStackTraces(jvmti, 15, &stack_info, &thread_count);
		}
		if (err != JVMTI_ERROR_NONE)
		{
			if ( err == JVMTI_ERROR_WRONG_PHASE && (*jvmti)->GetPhase(jvmti, &phase) == JVMTI_ERROR_NONE && phase == JVMTI_PHASE_DEAD ) {
				printf("Terminating stack trace\n");
				break;
			}

			check_jvmti_error(err, "Error while getting thread infos");
			continue;
		}
		// Number of int values following the initial state and size value
		int32_t data_size = 1;
		for (int i = 0; i < thread_count; i++)
		{
			//extracting some variables
			jvmtiStackInfo *infop = &stack_info[i];
			data_size += 3 + 3*infop->frame_count;
		}
		jlong currentOffset = currentPos + 1;
		memcpy(&g_dataBuffer[currentOffset], &data_size, sizeof(jint));
		SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
		//adding total thread count to the beginning (2nd slot) of buffer
		memcpy(&g_dataBuffer[currentOffset], &thread_count, sizeof(jint));
		SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
		for (int i = 0; i < thread_count; i++)
		{
			//extracting some variables
			jvmtiStackInfo *infop = &stack_info[i];
			jthread thread = infop->thread;
			jint tid = (*env)->GetLongField(env, thread, tidFieldId);
			jint state = infop->state;
			jint frame_count = infop->frame_count;
			jvmtiFrameInfo *frames = infop->frame_buffer;
			//thread info
			memcpy(&g_dataBuffer[currentOffset], &tid, sizeof(jint));
			SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
			memcpy(&g_dataBuffer[currentOffset], &state, sizeof(jint));
			SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
			memcpy(&g_dataBuffer[currentOffset], &frame_count, sizeof(jint));
			SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
			//each frame has 2 elements (method and location)
			for(int j=0; j<frame_count;j++)
			{
				jint methIdx = addMethodId(frames[j].method);
				memcpy(&g_dataBuffer[currentOffset],&methIdx, sizeof(jint));
				SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
				memcpy(&g_dataBuffer[currentOffset], &frames[j].location, sizeof(jint)); // it is the bytecode location
				SET_CURRENT_OFFSET(currentOffset, g_dataCapacity);
			} //end of frames loop
		} //end of threads loop
		//adding state of buffer to the beginning (1st slot) of buffer
		memcpy(&g_dataBuffer[currentPos], &tagD, sizeof(jint));
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
	if (threadClass == NULL)
	{
		printf("jclass error.");
	}
	jmethodID methodID = (*env)->GetMethodID(env, threadClass, "<init>", "()V"); // -> problem!
	if (methodID == NULL)
		printf("jmethodID error.");
	jthread threadObj = (*env)->NewObject(env, threadClass, methodID);
	if (obj == NULL)
		printf("jobject error.");
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
// The method which is executed when the agent is initialized (e.g. agent library loaded)
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
	printf("\nNATIVE: Agent was loaded!\n");
	// load JVMTI interface and set it to a global variable in order each method can use it
	jvmtiError error;
	error = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1_2);
	check_jvmti_error(error, "Error while getting JVMTI..");
	// init capabilities
	jvmtiCapabilities capa;
	jvmtiError err = (*jvmti)->GetCapabilities(jvmti, &capa);
	// set required caps. if no error occurred
	if (err == JVMTI_ERROR_NONE)
	{
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
