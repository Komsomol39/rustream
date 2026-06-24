#!/bin/sh
#
# Gradle startup script for UN*X
#
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
