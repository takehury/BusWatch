#!/bin/bash
# Host-side test suite: pure-Java unit tests, Java2D preview renders and an
# optional live end-to-end check against the Krasnoyarsk feed.
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-/tmp/jdk17home}"
export JAVA_HOME
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

rm -rf build/test-classes
mkdir -p build/test-classes

# Only pure-Java sources (the Android view/activity/location/repository are
# compiled by build_apk.sh against android.jar).
find app/src/ru/krsk/buswatch -name '*.java' \
  ! -name 'WatchView.java' ! -name 'BusWatchActivity.java' \
  ! -name 'LocationHelper.java' ! -name 'BusRepository.java' \
  ! -name 'BusAlertService.java' ! -name 'Prefs.java' ! -name 'Net.java' ! -name 'AlertAlarmReceiver.java' > build/srcs.txt
find app/tests -name '*.java' >> build/srcs.txt

"$JAVAC" --release 8 -encoding UTF-8 -d build/test-classes @build/srcs.txt

echo "######## UNIT TESTS ########"
"$JAVA" -cp build/test-classes ru.krsk.buswatch.AllTests tests/fixtures

echo
echo "######## UI PREVIEWS ########"
"$JAVA" -Djava.awt.headless=true -cp build/test-classes ru.krsk.buswatch.Preview tests/fixtures tests/preview

if [ "${1:-}" = "--live" ]; then
  echo
  echo "######## LIVE FEED ########"
  "$JAVA" -cp build/test-classes ru.krsk.buswatch.LiveCheck tests/fixtures | tee tests/reports/live_report.txt
fi

# не оставляем временные классы в рабочей директории
rm -rf build/test-classes build/srcs.txt
rmdir build 2>/dev/null || true
