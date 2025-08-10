#!/bin/bash
# Clojure Code Formatter Script
# Uses cljfmt to format all Clojure files in the project

echo "🎨 Formatting Clojure code with cljfmt..."

# Check if running via Babashka or Clojure
if command -v bb &> /dev/null; then
    echo "Using Babashka to run cljfmt..."
    bb -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
       -M -e "(require '[cljfmt.main :as fmt]) (fmt/-main \"fix\" \"src/\")" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✅ Formatting complete!"
    else
        echo "⚠️  Babashka formatting failed, trying Clojure CLI..."
        clojure -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
                -M -m cljfmt.main fix src/
    fi
elif command -v clojure &> /dev/null; then
    echo "Using Clojure CLI to run cljfmt..."
    clojure -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
            -M -m cljfmt.main fix src/
else
    echo "❌ Neither Babashka nor Clojure CLI found!"
    echo "Install with: brew install babashka"
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
    if command -v bb &> /dev/null; then
        bb -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
           -M -e "(require '[cljfmt.main :as fmt]) (fmt/-main \"check\" \"src/\")" 2>/dev/null
    else
        clojure -Sdeps '{:deps {cljfmt/cljfmt {:mvn/version "0.9.2"}}}' \
                -M -m cljfmt.main check src/
    fi
fi