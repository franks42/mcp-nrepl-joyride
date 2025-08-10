#!/bin/bash
# Python Formatter Script
# Uses UV to run black and isort for consistent Python formatting

echo "🐍 Formatting Python code..."

# Check if UV is available
if ! command -v uv &> /dev/null; then
    echo "❌ UV not found!"
    echo "Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"
    exit 1
fi

# Format with black
echo "📝 Running black formatter..."
uv run black .

if [ $? -ne 0 ]; then
    echo "⚠️ Black formatting failed"
    exit 1
fi

# Sort imports with isort
echo "📋 Sorting imports with isort..."
uv run isort .

if [ $? -ne 0 ]; then
    echo "⚠️ Import sorting failed"
    exit 1
fi

echo "✅ Python formatting complete!"