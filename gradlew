#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Check if we are running in Cygwin or MSYS/MinGW to avoid issues with paths
cygwin=false
msys=false
darwin=false
case "$(uname)" in
  CYGWIN* )
    cygwin=true
    ;;
  MINGW* | MSYS* )
    msys=true
    ;;
  Darwin* )
    darwin=true
    ;;
esac

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")"/$link"
  fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
    exit 1
  fi
else
  JAVACMD="java"
  which java >/dev/null 2>&1 || {
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
  }
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" ] ; then
  MAX_FD="maximum"
  if [ "$OS" = "Windows_NT" ] ; then
    MAX_FD=""
  fi
  if [ -n "$MAX_FD" ] ; then
    ulimit -n "$MAX_FD" >/dev/null 2>&1
  fi
fi

# Collect all arguments for the java command.
CLASSPATH="$CLASSPATH"

# For Cygwin or MSYS, switch paths to Windows format before running java.
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
  APP_HOME=$(cygpath --path --mixed "$APP_HOME")
  CLASSPATH=$(cygpath --path --mixed "$CLASSPATH")
  JAVACMD=$(cygpath --unix "$JAVACMD")
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS \
  -Dorg.gradle.appname=$APP_BASE_NAME \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
