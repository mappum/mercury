#!/bin/sh

PRG=$0

while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '^.*-> \(.*\)$' 2>/dev/null`
    if expr "$link" : '^/' 2> /dev/null >/dev/null; then
        PRG="$link"
    else
        PRG="`dirname "$PRG"`/$link"
    fi
done

progdir=`dirname "$PRG"`

if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
  elif [ -x "$JAVA_HOME/jre/bin/java" ]; then
    JAVACMD="$JAVA_HOME/jre/bin/java"
  fi
elif [ -x /usr/libexec/java_home ]; then
  JAVACMD="`/usr/libexec/java_home`/bin/java"
elif ( which java 2>&1 > /dev/null ); then
  JAVACMD="`which java`"
elif [ -x "/Library/Java/Home" ]; then
  JAVACMD="/Library/Java/Home/bin/java"
else
  JAVACMD="/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java"
fi

exec "$JAVACMD" -classpath "$progdir/../Resources/Jars/*" \
  -Dapple.laf.useScreenMenuBar=true \
  -Xdock:name="Mercury Wallet" \
  -Xdock:icon="$progdir/../Resources/icon.icns" \
  io.coinswap.client.Main
