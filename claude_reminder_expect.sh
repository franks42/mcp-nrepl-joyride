#!/usr/bin/expect -f

# Claude Wrapper with TODO.md Reminders using Expect
# Usage: ./claude_reminder_expect.sh [period_in_minutes] [claude_args...]
# Default period: 15 minutes

set timeout -1
set period_min 15
set reminder_file "claude_reminder.md"

# Parse period if provided
if {[string is integer [lindex $argv 0]]} {
    set period_min [lindex $argv 0]
    set claude_args [lrange $argv 1 end]
} else {
    set claude_args $argv
}

set period_sec [expr $period_min * 60]

# Function to read reminder content
proc get_reminder_content {reminder_file} {
    if {[file exists $reminder_file]} {
        set fp [open $reminder_file r]
        set content [read $fp]
        close $fp
        return $content
    } else {
        return "🚨 REMINDER: Use TODO.md for all todos, NOT TodoWrite tool!\n📝 File $reminder_file not found - using default message"
    }
}

# Function to send reminder
proc send_reminder {spawn_id reminder_file} {
    # Display the reminder to the user's terminal instead of injecting into Claude
    send_user "\n\n"
    send_user "════════════════════════════════════════════════════════════════\n"
    send_user "🔔 PERIODIC REMINDER (every $::period_min minutes):\n"
    send_user "────────────────────────────────────────────────────────────────\n"
    send_user "[get_reminder_content $reminder_file]\n"
    send_user "════════════════════════════════════════════════════════════════\n"
    send_user "📝 Copy and paste the above if you want Claude to see it\n\n"
}

# Start Claude Code
puts "🚨 Claude Wrapper Started - Reminder every $period_min minutes"
puts "📝 Reading reminders from: $reminder_file"
puts "🚀 Starting Claude Code...\n"

spawn $env(HOME)/.claude/local/claude {*}$claude_args
set claude_spawn_id $spawn_id

# Send initial reminder after short delay
after 3000
send_reminder $claude_spawn_id $reminder_file

# Set up periodic reminder timer
proc setup_timer {period_ms spawn_id reminder_file} {
    after $period_ms [list setup_timer $period_ms $spawn_id $reminder_file]
    send_reminder $spawn_id $reminder_file
}

# Start the timer (convert to milliseconds)
set period_ms [expr $period_sec * 1000]
after $period_ms [list setup_timer $period_ms $claude_spawn_id $reminder_file]

# Enable interaction between user and spawned process
interact