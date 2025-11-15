#!/bin/bash
# Format Clojure code using cljfmt

set -e

MODE="${1:-fix}"

case "$MODE" in
  check)
    echo "🔍 Checking Clojure code formatting..."
    cljfmt check src/
    echo "✅ All files are properly formatted!"
    ;;
  fix|*)
    echo "🎨 Formatting Clojure code..."
    cljfmt fix src/
    echo "✅ Code formatted successfully!"
    ;;
esac
