#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/app/src/main/java/br/com/educatransito/domain/ProtocolGenerator.kt" -d "$TMP/protocol.jar"
cat > "$TMP/Check.kt" <<'KOTLIN'
import br.com.educatransito.domain.ProtocolGenerator
fun main() {
    check(ProtocolGenerator.format(2026, 1) == "EDU-2026-000001")
    check(ProtocolGenerator.format(2026, 125) == "EDU-2026-000125")
    check(ProtocolGenerator.normalizePlate("abc-1d23") == "ABC1D23")
    check(runCatching { ProtocolGenerator.format(2026, 0) }.isFailure)
    println("PASS: 4 protocol and plate checks")
}
KOTLIN
kotlinc -classpath "$TMP/protocol.jar" "$TMP/Check.kt" -include-runtime -d "$TMP/check.jar"
java -cp "$TMP/check.jar:$TMP/protocol.jar" CheckKt
