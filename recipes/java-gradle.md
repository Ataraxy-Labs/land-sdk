# java-gradle

JDK 21 from Debian, the test classpath downloaded once through the repository's Gradle wrapper, test classes built
once and the tests run after them.

when: build.gradle.kts|build.gradle

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq --no-install-recommends openjdk-21-jdk-headless
export GRADLE_USER_HOME=/opt/gradle
./gradlew --no-daemon dependencies --configuration testRuntimeClasspath >/dev/null
chmod -R a+rwX /opt/gradle
```

## tests

```
# name  folder  files  command
build   .       -  GRADLE_USER_HOME=/opt/gradle ./gradlew --offline --no-daemon -q testClasses ;; keep
test    .       -  GRADLE_USER_HOME=/opt/gradle ./gradlew --offline --no-daemon test ;; after=build
```
