
SOURCES = \
	TTYEmulator.java \
	TTYOutput.java \
	TTYInput.java \
	IIRFilter.java

TARGET=tty-emulator.jar

$(TARGET): $(SOURCES) manifest CLASSDIR
	javac -g $(SOURCES) -d CLASSDIR
	jar cvfm $@ manifest -C CLASSDIR .

clean:
	rm -rf CLASSDIR
	rm $(TARGET)

CLASSDIR: 
	mkdir CLASSDIR
