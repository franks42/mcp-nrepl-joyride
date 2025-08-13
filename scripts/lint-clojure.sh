#!/bin/bash
# Clojure Linter Script
# Uses clj-kondo to lint all Clojure files in the project

echo "🔍 Linting Clojure code..."

if command -v clj-kondo &> /dev/null; then
    clj-kondo --lint src/
    exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        echo "✅ Clojure linting complete - no issues found!"
    elif [ $exit_code -eq 2 ]; then
        echo "⚠️ Clojure linting found warnings (but no errors)"
        echo "💡 Consider fixing warnings for better code quality"
        exit 0  # Don't fail on warnings
    elif [ $exit_code -eq 3 ]; then
        echo "❌ Clojure linting found ERRORS - must fix before committing"
        exit 1
    else
        echo "⚠️ Clojure linting completed with issues"
        exit 1
    fi
else
    echo "❌ clj-kondo not found!"
    echo "Install with: brew install borkdude/brew/clj-kondo"
    exit 1
fi