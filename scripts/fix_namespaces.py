#!/usr/bin/env python3
"""
Script to fix namespace naming throughout the codebase.
Changes nrepl-mcp_server to nrepl-mcp-server (proper Clojure conventions).
"""

import os
import re
from pathlib import Path


def fix_namespace_in_file(file_path):
    """Fix namespace names in a single file."""
    try:
        with open(file_path, "r") as f:
            content = f.read()

        # Track if we made any changes
        original_content = content

        # Replace the main namespace pattern: nrepl-mcp_server -> nrepl-mcp-server
        content = re.sub(r"nrepl-mcp_server", "nrepl-mcp-server", content)

        # Replace mcp_server -> mcp-server in require aliases
        content = re.sub(r"mcp_server/", "mcp-server/", content)
        content = re.sub(r":as mcp_server", ":as mcp-server", content)

        # Write back if changes were made
        if content != original_content:
            with open(file_path, "w") as f:
                f.write(content)
            print(f"✅ Fixed: {file_path}")
            return True
        else:
            print(f"⏭️  No changes: {file_path}")
            return False

    except Exception as e:
        print(f"❌ Error processing {file_path}: {e}")
        return False


def main():
    """Fix all Clojure files in the src directory."""
    src_dir = Path("src")

    if not src_dir.exists():
        print("❌ src directory not found")
        return 1

    # Find all .clj files
    clj_files = list(src_dir.glob("**/*.clj"))

    print(f"🔍 Found {len(clj_files)} Clojure files")
    print()

    fixed_count = 0
    for file_path in clj_files:
        if fix_namespace_in_file(file_path):
            fixed_count += 1

    print()
    print(f"🎉 Fixed {fixed_count} files out of {len(clj_files)} total")

    return 0


if __name__ == "__main__":
    exit(main())
