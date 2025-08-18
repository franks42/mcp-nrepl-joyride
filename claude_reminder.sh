#!/bin/bash

# Claude Wrapper with TODO.md Reminders
# Usage: ./claude_reminder.sh [period_in_minutes] [claude_args...]
# Default period: 15 minutes

# Parse period parameter (default 15 minutes)
PERIOD_MIN=15
if [[ "$1" =~ ^[0-9]+$ ]]; then
    PERIOD_MIN=$1
    shift  # Remove period from arguments
fi

PERIOD_SEC=$((PERIOD_MIN * 60))
REMINDER_FILE="claude_reminder.md"

# Function to show reminder content
show_reminder() {
    # echo ""
    # echo "==============================================="
    # echo "⏰ $(date '+%H:%M:%S') - AUTOMATED REMINDER"
    # echo "==============================================="
    
    if [[ -f "$REMINDER_FILE" ]]; then
        cat "$REMINDER_FILE"
    else
        echo "🚨 REMINDER: Use TODO.md for all todos, NOT TodoWrite tool!"
        echo "📝 File $REMINDER_FILE not found - using default message"
    fi
    
    # echo "==============================================="
    # echo ""
}

# Show initial reminder
echo "🚨 Claude Wrapper Started - Reminder every $PERIOD_MIN minutes"
echo "📝 Reading reminders from: $REMINDER_FILE"
echo ""


# Function to inject initial reminder 
inject_initial_reminder() {
    echo ""
    echo ""
    sleep 5
    echo "Hi Claude Code!"
    sleep 5
    show_reminder
}

# Function to inject reminders periodically 
inject_reminder() {
    while true; do
        sleep $PERIOD_SEC
        show_reminder
    done
}

# Start background reminder process
inject_initial_reminder &
REMINDER_PID1=$!
inject_reminder &
REMINDER_PID2=$!

# Cleanup function
cleanup() {
    echo ""
    echo "🛑 Stopping reminder process..."
    kill $REMINDER_PID1 2>/dev/null
    kill $REMINDER_PID2 2>/dev/null
    exit 0
}

# Set up signal handlers for cleanup
trap cleanup INT TERM EXIT

# Start actual Claude with all remaining arguments passed through
echo "🚀 Starting Claude Code..."
echo ""
# claude "$@"
$HOME/.claude/local/claude "$@"

# Cleanup happens automatically via trap