#!/usr/bin/env sh
# Builds the backend and starts the app on http://localhost:8080
cd "$(dirname "$0")" || exit 1
command -v mvn >/dev/null 2>&1 || { echo "Install JDK 17+ and Maven 3.9+ first."; exit 1; }
mvn -q -f backend/pom.xml package -DskipTests && java -jar backend/target/recsys.jar
