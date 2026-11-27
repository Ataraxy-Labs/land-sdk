# java-maven

JDK 21 and Maven from Debian, dependencies downloaded once so tests run offline, test classes built once and the tests
run after them.

when: pom.xml

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq --no-install-recommends openjdk-21-jdk-headless
apt-get install -y -qq --no-install-recommends maven
mvn -B -q -Dmaven.repo.local=/opt/m2 dependency:go-offline
chmod -R a+rwX /opt/m2
```

## tests

```
# name  folder  files  command
build   .       -  mvn -o -B -q -Dmaven.repo.local=/opt/m2 test-compile ;; keep
test    .       -  mvn -o -B -q -Dmaven.repo.local=/opt/m2 surefire:test ;; after=build
```
