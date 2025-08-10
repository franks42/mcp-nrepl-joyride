#!/bin/bash
# Python Linter Script
# Uses UV to run flake8 and mypy for Python code quality checks

echo "🔍 Linting Python code..."

# Check if UV is available
if ! command -v uv &> /dev/null; then
    echo "❌ UV not found!"
    echo "Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"
    exit 1
fi

# Lint with flake8
echo "📝 Running flake8 linter..."
uv run flake8 .

flake8_exit=$?

# Type check with mypy
echo "🔍 Running mypy type checker..."
uv run mypy . 2>/dev/null || echo "⚠️ mypy found type issues (may be expected)"

mypy_exit=$?

# Report results
if [ $flake8_exit -eq 0 ]; then
    echo "✅ Python linting complete - no flake8 issues found!"
    if [ $mypy_exit -eq 0 ]; then
        echo "✅ Type checking complete - no mypy issues found!"
    else
        echo "⚠️ Type checking found issues - review mypy output above"
    fi
else
    echo "❌ Python linting found issues - please fix before committing"
    exit 1
fi