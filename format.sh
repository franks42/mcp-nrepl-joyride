#!/bin/bash
# Clojure Code Formatter Script
# Uses cljfmt to format all Clojure files in the project

echo "🎨 Formatting Clojure code with cljfmt..."

# Use Clojure CLI directly for cljfmt (requires JVM features)
if command -v clojure &> /dev/null; then
    clojure -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
            -M -m cljfmt.main fix src/
else
    echo "❌ Clojure CLI not found!"
    echo "Install with: brew install clojure/tools/clojure"
    exit 1
fi

echo ""
echo "💡 To check formatting without fixing:"
echo "   ./format.sh check"
echo ""
echo "📝 Configuration: .cljfmt.edn"

# Optional: Run check mode
if [ "$1" = "check" ]; then
    echo "🔍 Checking formatting (no changes)..."
    clojure -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
            -M -m cljfmt.main check src/
fi