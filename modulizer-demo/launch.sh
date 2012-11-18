#!/bin/bash

L=target/modulizer-demo-1.0.0-SNAPSHOT-launcher.jar

OPTS="-DNOmodulizer.logging=file:modulizer.log|socket://localhost:7777 -Dlogging.level=WARN"

case "$OSTYPE" in
  cygwin*)
    L=$(cygpath -wal $L)
    ;;
  linux*)
    ;;
  darwin*)
    OPTS="$OPTS -Djboss.modules.system.pkgs=com.apple.laf,com.apple.laf.resources"
    ;;
esac


java $OPTS -jar "$L" $@
