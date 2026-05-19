#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------------------
# FileSeek install script
# Builds the fat jar and installs a wrapper to ~/bin
# or /usr/local/bin (if run with sudo).
# -------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"
JAR_TARGET="target/fileseek.jar"
JAR_DEST_DIR="$HOME/.fileseek/bin"
WRAPPER="/usr/local/bin/fileseek"
LOCAL_WRAPPER="$HOME/bin/fileseek"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}==>${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
err()  { echo -e "${RED}[error]${NC} $1"; exit 1; }
err()  { echo -e "${RED}[error]${NC} $1"; exit 1; }

# --- Requirements ---
command -v java  >/dev/null 2>&1 || err "Java not found. Install Java 17+."
command -v mvn   >/dev/null 2>&1 || err "Maven not found. Install Maven 3.8+."

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    err "Java 17+ required. Found Java $JAVA_VERSION."
fi

# --- Build ---
log "Building FileSeek..."
mvn package -q -DskipTests || err "Build failed. Run 'mvn package' to see errors."
log "Build complete."

# --- Install jar ---
mkdir -p "$JAR_DEST_DIR"
cp "$JAR_TARGET" "$JAR_DEST_DIR/fileseek.jar"
log "Jar installed to $JAR_DEST_DIR/fileseek.jar"

# --- Install wrapper ---
WRAPPER_CONTENT="#!/usr/bin/env bash
exec java -jar \"$JAR_DEST_DIR/fileseek.jar\" \"\$@\""

install_global() {
    echo "$WRAPPER_CONTENT" | sudo tee "$WRAPPER" > /dev/null
    sudo chmod +x "$WRAPPER"
    log "Installed to $WRAPPER (system-wide)"
}

install_local() {
    mkdir -p "$HOME/bin"
    echo "$WRAPPER_CONTENT" > "$LOCAL_WRAPPER"
    chmod +x "$LOCAL_WRAPPER"
    log "Installed to $LOCAL_WRAPPER (user-only)"

    # Check if ~/bin is on PATH
    if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
        warn "~/bin is not on your PATH."
        warn "Add this to your shell profile:"
        warn "  export PATH=\"\$HOME/bin:\$PATH\""
    fi
}

if [ "$EUID" -eq 0 ]; then
    install_global
elif command -v sudo >/dev/null 2>&1; then
    read -rp "Install system-wide to /usr/local/bin? (requires sudo) [y/N]: " choice
    if [[ "$choice" =~ ^[Yy]$ ]]; then
        install_global
    else
        install_local
    fi
else
    install_local
fi

echo ""
log "FileSeek installed successfully."
echo ""
echo "  fileseek --help         show all commands"
echo "  fileseek add ~/Projects index a directory"
echo "  fileseek search \"redis\" search the index"
echo "  fileseek watch          live index updates"
echo ""