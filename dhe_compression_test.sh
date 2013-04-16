#!/bin/sh
# Invokes the Compression Test Class
DIR="$( cd "$( dirname "$0" )" && pwd )"
mvn -f $DIR/pom.xml -q exec:java -Dexec.mainClass="DheCompressionTest" 
