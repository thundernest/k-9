#!/bin/bash

## This script installs mdbook and extensions, additionally it downloads the latest Mermaid.js version.
## If the script is run with the "--force" argument, it will force the installation of mdbook and it's extensions.

set -e

# Define installation paths
MERMAID_JS_DIR="docs/assets/theme/"
MERMAID_JS_PATH="${MERMAID_JS_DIR}mermaid.min.js"

# Check if the script was run with "force" argument
FORCE_UPDATE=false
if [ "$1" == "--force" ]; then
    FORCE_UPDATE=true
    echo "Force update mode enabled."
fi

# Ensure Cargo (Rust) is installed
if ! command -v cargo &> /dev/null; then
    echo "Cargo (Rust) is required to install mdbook"
    echo "Please install Rust from https://www.rust-lang.org/tools/install."
    exit 1
fi

# Install mdbook
if $FORCE_UPDATE; then
    echo "Forcing mdbook installation..."
    cargo install --force mdbook
    cargo install --force mdbook-external-links
    cargo install --force mdbook-mermaid
    cargo install --force mdbook-last-changed
    cargo install --force mdbook-pagetoc
else
    cargo install mdbook
    cargo install mdbook-external-links
    cargo install mdbook-mermaid
    cargo install mdbook-last-changed
    cargo install mdbook-pagetoc
fi

# Fetch latest releases from GitHub
LATEST_RELEASES=$(curl -s "https://api.github.com/repos/mermaid-js/mermaid/releases" | jq -r '.[].tag_name')

# Extract the latest valid mermaid version (filtering out layout-elk)
LATEST_MERMAID_VERSION=$(echo "$LATEST_RELEASES" | grep -E '^mermaid@[0-9]+\.[0-9]+\.[0-9]+$' | sed 's/mermaid@//' | sort -V | tail -n 1)

if [ -z "$LATEST_MERMAID_VERSION" ]; then
    echo "Failed to fetch the latest Mermaid.js version."
    exit 1
fi

mkdir -p "$MERMAID_JS_DIR"

# Download the latest Mermaid.js
echo "Downloading Mermaid.js version $LATEST_MERMAID_VERSION..."
curl -L "https://cdn.jsdelivr.net/npm/mermaid@$LATEST_MERMAID_VERSION/dist/mermaid.min.js" -o "$MERMAID_JS_PATH"

echo "Installation and update complete!"
