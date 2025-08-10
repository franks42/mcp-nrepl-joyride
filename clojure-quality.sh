#!/bin/bash
# Clojure Code Quality Script
# Combined formatting and linting workflow for consistent code quality

echo "🎨 Clojure code quality check..."

# Step 1: Format code first
echo "📝 Step 1: Formatting code with cljfmt..."
./format.sh

# Step 2: Lint for issues
echo "🔍 Step 2: Linting code with clj-kondo..."
./lint-clojure.sh

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 Clojure code quality check PASSED!"
    echo "✅ Code is properly formatted and has no linting issues"
else
    echo ""
    echo "⚠️ Clojure code quality check FAILED!"
    echo "❌ Fix linting issues and run ./clojure-quality.sh again"
    echo ""
    echo "Workflow to fix issues:"
    echo "  1. Fix the reported issues"
    echo "  2. Run ./clojure-quality.sh again"
    echo "  3. Repeat until all checks pass"
    exit 1
fi