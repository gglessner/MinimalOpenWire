#!/bin/sh

javac -cp .:$(echo *.jar | sed 's/ /:/g') MinimalOpenWire.java
java -cp .:$(echo *.jar | sed 's/ /:/g') MinimalOpenWire
