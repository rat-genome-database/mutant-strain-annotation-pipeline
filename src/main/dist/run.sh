#!/usr/bin/env bash
. /etc/profile

APPNAME=mutant-strain-annotation-pipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST="mtutaj@mcw.edu,sjwang@mcw.edu"
fi

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@" > $APPDIR/run.log 2>&1

mailx -s "[$SERVER] Mutant Strain Annotation pipeline ok" $EMAILLIST < $APPDIR/logs/summary.log
