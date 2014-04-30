#!/bin/bash

java -classpath ../target/gaulish_olympics_three-1.0-SNAPSHOT-jar-with-dependencies.jar so.modernized.dos.ConcreteFrontend 'akka.tcp://db@127.0.0.1:1337' 2 $1 $2 0 0
