# Define a variable for classpath
CLASS_PATH = .

CURRENT=$(!C:)
JAVA_HOME=C:/Program Files/Java/jdk1.8.0_144
JAVA=$(JAVA_HOME)/bin/java.exe
JAVAC=$(JAVA_HOME)/bin/javac.exe
JAVAH=$(JAVA_HOME)/bin/javah.exe
GCC=C:/cygwin64/bin/x86_64-w64-mingw32-gcc-6.4.0.exe
OPTIONS=-Wl,--add-stdcall-alias
SYSINC=win32

# Define a virtual path for .class in the bin directory
vpath %.class $(CLASS_PATH)

all : clean test

# $@ matches the target, $< matches the first dependancy
jnitestMAKEFILE.dll : jnitestMAKEFILE.o
	"$(GCC)" -m64  -shared -o $@ $<

# $@ matches the target, $< matches the first dependancy
jnitestMAKEFILE.o : jnitest.c JNITest.h
	"$(GCC)" -m64 -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(SYSINC)" -D__int64="long long" -c $< -o $@

# $* matches the target filename without the extension
JNITest.h : JNITest.class
	"$(JAVAH)" -classpath $(CLASS_PATH) $*

JNITest.class: JNITest.java
	"$(JAVAC)" -d . JNITest.java
	
clean :
	rm -f JNITest.h jnitestMAKEFILE.o jnitestMAKEFILE.dll JNITest.class
	
test: jnitestMAKEFILE.dll JNITest.class
	"$(JAVA)" -agentpath:"$(CURRENT)/jnitestMAKEFILE.dll" -classpath . JNITest