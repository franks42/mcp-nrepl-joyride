#!/usr/bin/env python3
"""
Claude Code Wrapper with Automated Reminders
Injects reminder content into Claude's input stream at regular intervals.
"""

import os
import sys
import time
import threading
import subprocess
import select
import pty
import termios
import tty
from pathlib import Path

# Configuration
DEFAULT_PERIOD_MIN = 15
REMINDER_FILE = "claude_reminder.md"
CLAUDE_PATH = os.path.expanduser("~/.claude/local/claude")

def get_reminder_content():
    """Read reminder content from file or return default message."""
    reminder_path = Path(REMINDER_FILE)
    if reminder_path.exists():
        return reminder_path.read_text().strip()
    else:
        return "🚨 REMINDER: Use TODO.md for all todos, NOT TodoWrite tool!\n📝 File not found - using default message"

def inject_reminder(master_fd):
    """Inject reminder content into the PTY master."""
    reminder = get_reminder_content()
    
    # First, clear any partial input with Ctrl-U
    os.write(master_fd, b'\x15')  # Ctrl-U to clear line
    time.sleep(0.05)
    
    # Send the reminder text
    for line in reminder.split('\n'):
        os.write(master_fd, line.encode('utf-8'))
        os.write(master_fd, b'\r\n')  # CR+LF for each line
        time.sleep(0.02)
    
    # Send an extra Enter to submit the message
    os.write(master_fd, b'\r')
    time.sleep(0.05)

def reminder_thread(master_fd, period_sec, stop_event):
    """Background thread to inject reminders periodically."""
    # Initial reminder after 5 seconds
    time.sleep(5)
    if not stop_event.is_set():
        inject_reminder(master_fd)
    
    # Periodic reminders
    while not stop_event.is_set():
        if stop_event.wait(period_sec):
            break
        inject_reminder(master_fd)

def main():
    # Parse arguments
    args = sys.argv[1:]
    period_min = DEFAULT_PERIOD_MIN
    
    if args and args[0].isdigit():
        period_min = int(args[0])
        claude_args = args[1:]
    else:
        claude_args = args
    
    period_sec = period_min * 60
    
    print(f"🚨 Claude Wrapper Started - Reminder every {period_min} minutes")
    print(f"📝 Reading reminders from: {REMINDER_FILE}")
    print("🚀 Starting Claude Code...\n")
    
    # Create a pseudo-terminal
    master_fd, slave_fd = pty.openpty()
    
    # Start Claude Code with the slave PTY
    process = subprocess.Popen(
        [CLAUDE_PATH] + claude_args,
        stdin=slave_fd,
        stdout=slave_fd,
        stderr=slave_fd,
        preexec_fn=os.setsid
    )
    
    # Close slave FD in parent process
    os.close(slave_fd)
    
    # Save terminal settings
    old_tty = termios.tcgetattr(sys.stdin)
    
    try:
        # Set terminal to raw mode
        tty.setraw(sys.stdin.fileno())
        
        # Start reminder thread
        stop_event = threading.Event()
        reminder = threading.Thread(
            target=reminder_thread,
            args=(master_fd, period_sec, stop_event),
            daemon=True
        )
        reminder.start()
        
        # Main I/O loop
        while process.poll() is None:
            # Check for available input
            r, w, e = select.select([sys.stdin, master_fd], [], [], 0.1)
            
            if sys.stdin in r:
                # Forward user input to Claude
                data = os.read(sys.stdin.fileno(), 1024)
                if not data:
                    break
                os.write(master_fd, data)
            
            if master_fd in r:
                # Forward Claude output to terminal
                try:
                    data = os.read(master_fd, 1024)
                    if data:
                        os.write(sys.stdout.fileno(), data)
                    else:
                        break
                except OSError:
                    break
    
    except KeyboardInterrupt:
        print("\n\n🛑 Interrupted by user")
    
    finally:
        # Restore terminal settings
        termios.tcsetattr(sys.stdin, termios.TCSADRAIN, old_tty)
        
        # Clean up
        stop_event.set()
        os.close(master_fd)
        
        # Terminate Claude if still running
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
        
        print("\n🛑 Claude Code wrapper stopped")

if __name__ == "__main__":
    main()