#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -encoding UTF-8 -d "$test_dir" \
  app/src/main/java/cn/edu/buaa/ledger/{Entry,Bill,LedgerRules,CsvCodec}.java \
  app/src/main/java/cn/edu/buaa/ledger/domain/CategoryCatalog.java \
  app/src/main/java/cn/edu/buaa/ledger/domain/CampusParser.java \
  tests/CoreTests.java
java -cp "$test_dir" cn.edu.buaa.ledger.CoreTests
