#!/bin/bash
##mvn deploy:deploy-file  -Dfile=/Users/sumeetrohatgi/Downloads/sqljdbc_4.0/enu/sqljdbc4.jar -DartifactId=sqljdbc4 -Dversion=4.0 -DgroupId=sqljdbc4 -Dpackaging=jar -Durl=file:repo
mvn deploy:deploy-file  -Dfile=$1 -DartifactId=$(basename -s jar $1) -Dversion=$2 -DgroupId=$(basename -s jar $1) -Dpackaging=jar -Durl=file:repo
