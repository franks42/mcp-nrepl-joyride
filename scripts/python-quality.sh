#!/bin/bash
# Python Code Quality Script
# Combined formatting and linting workflow for consistent Python code quality

echo "🎨 Python code quality check..."

# Step 1: Format code first
echo "📝 Step 1: Formatting Python code..."
./scripts/format-python.sh

if [ $? -ne 0 ]; then
    echo "❌ Python formatting failed!"
    exit 1
fi

# Step 2: Lint for issues
echo "🔍 Step 2: Linting Python code..."
./scripts/lint-python.sh

if [ $? -eq 0 ]; then
    echo ""
    echo "🚨 MANUAL CHECK REQUIRED:"
    echo "   📋 Verify no blank lines between decorators and functions!"
    echo "   📋 Many linters miss this but it causes obscure runtime errors"
    echo ""
    echo "🎉 Python code quality check PASSED!"
    echo "✅ Code is properly formatted and has no linting issues"
else
    echo ""
    echo "⚠️ Python code quality check FAILED!"
    echo "❌ Fix linting issues and run ./scripts/python-quality.sh again"
    echo ""
    echo "Workflow to fix issues:"
    echo "  1. Fix the reported issues"
    echo "  2. 🚨 Check decorator spacing manually"
    echo "  3. Run ./scripts/python-quality.sh again"
    echo "  4. Repeat until all checks pass"
    exit 1
fi