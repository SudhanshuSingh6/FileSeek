#!/usr/bin/env bash
set -euo pipefail

JAR_DEST_DIR="$HOME/.fileseek/bin"
COMPLETION_FILE="$HOME/.fileseek/completion.sh"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}==>${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
err()  { echo -e "${RED}[error]${NC} $1"; exit 1; }

# --- requirements ---
command -v java >/dev/null 2>&1 || err "Java not found. Install Java 17+."
command -v mvn  >/dev/null 2>&1 || err "Maven not found. Install Maven 3.8+."

JAVA_VER=$(java -version 2>&1 | awk -F'"' '/version/{print $2}' | cut -d'.' -f1)

[ "$JAVA_VER" -ge 17 ] 2>/dev/null \
    || err "Java 17+ required. Found Java $JAVA_VER."

# --- project root ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT" || err "Could not enter project root."

# --- build ---
log "Building FileSeek..."

mvn package -q -DskipTests \
    || err "Build failed. Run 'mvn package' for details."

log "Build complete."

# --- install jar ---
mkdir -p "$JAR_DEST_DIR"

cp target/fileseek.jar "$JAR_DEST_DIR/fileseek.jar"

log "Installed jar to $JAR_DEST_DIR/fileseek.jar"

WRAPPER="#!/usr/bin/env bash
FILESEEK_OPTS=\"\${FILESEEK_OPTS:--Xmx512m -Xms64m -XX:+UseG1GC -XX:+TieredCompilation}\"
exec java \$FILESEEK_OPTS -jar \"$JAR_DEST_DIR/fileseek.jar\" \"\$@\"
"

install_global() {
    echo "$WRAPPER" | sudo tee /usr/local/bin/fileseek > /dev/null
    sudo chmod +x /usr/local/bin/fileseek

    log "Installed to /usr/local/bin/fileseek (system-wide)"
}

install_local() {
    mkdir -p "$HOME/bin"

    echo "$WRAPPER" > "$HOME/bin/fileseek"

    chmod +x "$HOME/bin/fileseek"

    log "Installed to ~/bin/fileseek (user-only)"

    if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
        warn "~/bin is not on your PATH."
        warn "Add this to your shell profile:"
        warn "export PATH=\"\$HOME/bin:\$PATH\""
    fi
}

if [ "$EUID" -eq 0 ]; then

    install_global

elif command -v sudo >/dev/null 2>&1; then

    read -rp "Install system-wide? (requires sudo) [y/N]: " choice

    if [[ "$choice" =~ ^[Yy]$ ]]; then
        install_global
    else
        install_local
    fi

else

    install_local

fi

# --- shell completion ---
log "Generating shell completion..."

mkdir -p "$(dirname "$COMPLETION_FILE")"

PICOCLI_JAR="$HOME/.m2/repository/info/picocli/picocli/4.7.6/picocli-4.7.6.jar"

java -cp "$JAR_DEST_DIR/fileseek.jar:$PICOCLI_JAR" \
    picocli.AutoComplete \
    -f \
    -o "$COMPLETION_FILE" \
    com.fileseek.cli.FileSeekCommand \
    || warn "Could not generate completion."

SHELL_NAME=$(basename "$SHELL")
PROFILE=""

case "$SHELL_NAME" in
    zsh)
        PROFILE="$HOME/.zshrc"
        ;;
    bash)
        PROFILE="$HOME/.bashrc"
        ;;
esac

if [ -n "$PROFILE" ]; then
    if [ "$SHELL_NAME" = "zsh" ]; then
        grep -q "FileSeek completion" "$PROFILE" 2>/dev/null || {
            {
                echo ""
                echo "# FileSeek completion"
                echo "autoload -Uz compinit"
                echo "compinit"
                echo "autoload -U bashcompinit"
                echo "bashcompinit"
                echo "source \"$COMPLETION_FILE\""
            } >> "$PROFILE"
        }

    else

        SOURCE_LINE="source \"$COMPLETION_FILE\""

        grep -qF "$SOURCE_LINE" "$PROFILE" 2>/dev/null || {
            {
                echo ""
                echo "# FileSeek completion"
                echo "$SOURCE_LINE"
            } >> "$PROFILE"
        }

    fi

    log "Shell completion configured."

else
    warn "Unknown shell '$SHELL_NAME'."
    warn "Manually add:"
    warn "source \"$COMPLETION_FILE\""

fi