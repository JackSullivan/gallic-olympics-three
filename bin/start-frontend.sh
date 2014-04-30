#!/bin/bash

java -classpath ../target/gaulish_olympics_three-1.0-SNAPSHOT-jar-with-dependencies.jar so.modernized.dos.ConcreteProcess 'akka.tcp://db@127.0.0.1:1337' 2 $2 $3 
