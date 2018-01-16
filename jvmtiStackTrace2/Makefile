# Define a variable for classpath
CLASS_PATH = ../bin

# Define a virtual path for .class in the bin directory
vpath %.class $(CLASS_PATH)

all : jnitestMAKEFILE.dll

# $@ matches the target, $< matches the first dependancy
jnitestMAKEFILE.dll : jnitestMAKEFILE.o
	gcc -m64 -Wl,--add-stdcall-alias -shared -o $@ $<

# $@ matches the target, $< matches the first dependancy
jnitestMAKEFILE.o : jnitest.c JNITest.h
	gcc -m64 -I"C:\Program Files\Java\jdk1.8.0_144\include" -I"C:\Program Files\Java\jdk1.8.0_144\include\win32" -D__int64="long long" -c $< -o $@

# $* matches the target filename without the extension
JNITest.h : JNITest.class
	javah -classpath $(CLASS_PATH) $*

clean :
	rm JNITest.h jnitestMAKEFILE.o jnitestMAKEFILE.dll