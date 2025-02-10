@echo off
REM Set the classpath by listing all JAR files in the current directory
set CLASSPATH=.
for %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

REM Compile the Java file
javac -cp %CLASSPATH% MinimalOpenWire.java

REM Run the Java program
java -cp %CLASSPATH% MinimalOpenWire

REM Pause to keep the command window open if there's an error
pause
