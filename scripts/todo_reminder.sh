#!/bin/bash

# TODO.md Reminder Script
# Reminds Claude every 15 minutes to use TODO.md instead of context memory

echo "🚨 TODO.md Reminder Script Started"
echo "This will remind you every 15 minutes to use TODO.md for todos"
echo "Press Ctrl+C to stop"
echo ""

# Function to show the reminder
show_reminder() {
    echo "=================================================="
    echo "⏰ $(date '+%H:%M:%S') - TODO REMINDER"
    echo "=================================================="
    echo "🚨 CLAUDE: Use TODO.md for ALL todo management!"
    echo ""
    echo "❌ DO NOT use TodoWrite tool or context memory"
    echo "✅ DO edit TODO.md directly for all todo changes"
    echo "✅ DO mark completed items with [x] in TODO.md"
    echo "✅ DO update status/progress in TODO.md"
    echo ""
    echo "📍 Current location: $(pwd)/TODO.md"
    echo "📝 Last modified: $(stat -f '%Sm' TODO.md 2>/dev/null || echo 'File not found')"
    echo "=================================================="
    echo ""
}

# Show initial reminder
show_reminder

# Main loop - remind every 15 minutes (900 seconds)
while true; do
    sleep 900  # 15 minutes
    show_reminder
done