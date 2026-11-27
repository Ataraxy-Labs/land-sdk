# ruby-bundler

Ruby from Debian, gems installed with Bundler once, one unit per RSpec or Minitest file.

when: Gemfile

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq ruby ruby-dev build-essential
gem install --no-document bundler
bundle install --jobs 4
```

## tests

```
# name  folder  files  command
spec    .       spec/**/*_spec.rb  bundle exec rspec {file}
```

## tests

```
# name  folder  files  command
test    .       test/**/{*_test.rb,test_*.rb}  bundle exec ruby -w -Itest {file}
```

## tests

```
# name  folder  files  command
spec    .       -  bundle exec rspec
```
