# node-yarn-berry

Node 24 from nodejs.org, dependencies from yarn.lock with Yarn 2 or newer (the repository has a .yarnrc.yml). One
unit per Vitest or Jest file, or the package's own test script when it uses neither.

when: package.json yarn.lock .yarnrc.yml
group: node

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates xz-utils
curl -fsSL https://nodejs.org/dist/v24.11.1/node-v24.11.1-linux-x64.tar.xz | tar -xJ -C /usr/local --strip-components=1
corepack enable
yarn install --immutable
```

## tests vitest if package.json has "vitest"

```
# name  folder  files  command
spec    .       **/*.{test,spec}.{ts,tsx,js,jsx,mts,cts,mjs,cjs}  node_modules/.bin/vitest run {file}
```

## tests jest if package.json has "jest"

```
# name  folder  files  command
spec    .       **/*.{test,spec}.{ts,tsx,js,jsx,mts,cts,mjs,cjs}  node_modules/.bin/jest --coverage=false --verbose=false --runTestsByPath {file}
```

## tests test-script

```
# name  folder  files  command
test    .       -  yarn test
```
