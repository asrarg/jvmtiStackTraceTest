# Define a variable for classpath
CLASS_PATH = .

CURRENT="C:/Users/Peter\ Heusch/Teaching/Bachelor/Gadi/jvmtiStackTrace2"
JAVA_HOME=C:/Program Files/Java/jdk1.8.0_131
JAVA=$(JAVA_HOME)/bin/java.exe
JAVAC=$(JAVA_HOME)/bin/javac.exe
JAVAH=$(JAVA_HOME)/bin/javah.exe
GCC=C:/Users/cygwin/bin/x86_64-w64-mingw32-gcc-6.4.0.exe
OPTIONS=-Wl,--add-stdcall-alias
SYSINC=win32

# Define a virtual path for .class in the bin directory
vpath %.class $(CLASS_PATH)

all : test

# $@ matches the target, $< matches the first dependancy
StackTrace.dll : StackTrace.o
	"$(GCC)" -m64  -shared -o $@ $<

# $@ matches the target, $< matches the first dependancy
StackTrace.o : StackTrace.c StackTrace.h
	"$(GCC)" -m64 -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(SYSINC)" -D__int64="long long" -c $< -o $@

# $* matches the target filename without the extension
StackTrace.h : StackTrace.class
	"$(JAVAH)" -classpath $(CLASS_PATH) $*

StackTrace.class: StackTrace.java
	"$(JAVAC)" -d . StackTrace.java
	
clean :
	rm -f StackTrace.h StackTrace.o StackTrace.dll StackTrace.class
	
test: StackTrace.dll StackTrace.class
	"$(JAVA)" -agentpath:"$(CURRENT)/StackTrace.dll" -classpath . StackTrace