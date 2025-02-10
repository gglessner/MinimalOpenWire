#!/bin/sh

javac -cp .:$(echo ./jars/*.jar | sed 's/ /:/g') MinimalOpenWire.java
java -cp .:$(echo ./jars/*.jar | sed 's/ /:/g') MinimalOpenWire
