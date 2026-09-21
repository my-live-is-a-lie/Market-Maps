#!/bin/sh

# Gradle start script (simplified for CI reliability)

app_path=$0
while [ -h "$app_path" ]; do
  ls=$(ls -ld "$app_path")
  link=${ls#*' -> '}
  case $link in
    /*) app_path=$link ;;
    *) app_path=$(dirname "$app_path")/$link ;;
  esac
done
APP_HOME=$(cd "$(dirname "$app_path")" && pwd -P) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    JAVACMD=$JAVA_HOME/jre/sh/java
  else
    JAVACMD=$JAVA_HOME/bin/java
  fi
  if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
    exit 1
  fi
else
  JAVACMD=java
  if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
  fi
fi

if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: Could not find gradle-wrapper.jar at $CLASSPATH" >&2
  exit 1
fi

exec "$JAVACMD" \
  -Xmx2048m \
  -Xms256m \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
