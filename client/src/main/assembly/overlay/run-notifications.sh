#!/bin/bash

PATH_TO_CODE_BASE=`pwd`

JAVA_OPTS="-Djava.rmi.server.codebase=file://$PATH_TO_CODE_BASE/lib/jars/tpe1-g6-client-1.0-SNAPSHOT.jar"

MAIN_CLASS="ar.edu.itba.pod.client.FlightNotificationsClient"

java $JAVA_OPTS -cp 'lib/jars/*' $* $MAIN_CLASS