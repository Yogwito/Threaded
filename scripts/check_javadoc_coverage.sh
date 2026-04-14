#!/usr/bin/env bash

set -euo pipefail

if ! command -v rg >/dev/null 2>&1; then
    echo "Error: se requiere 'rg' (ripgrep) para ejecutar este chequeo." >&2
    exit 2
fi

if ! command -v perl >/dev/null 2>&1; then
    echo "Error: se requiere 'perl' para ejecutar este chequeo." >&2
    exit 2
fi

if [ "$#" -eq 0 ]; then
    set -- src/main/java src/test/java
fi

java_files=()
while IFS= read -r file; do
    if [ -n "$file" ]; then
        java_files+=("$file")
    fi
done < <(rg --files "$@" -g '*.java' | sort)

if [ "${#java_files[@]}" -eq 0 ]; then
    echo "No se encontraron archivos Java en las rutas indicadas." >&2
    exit 2
fi

read -r -d '' PERL_CHECK <<'PERL' || true
while (
    /(^|\n)([ \t]*)(?:@[\w$.()\"", =]+\n[ \t]*)*
    (
        (?:public|protected|private|static|final|abstract|sealed|non-sealed|synchronized|native|strictfp|transient|volatile|default|enum|class|interface|record)[^{;\n]*\([^;{\n]*\)\s*\{
      |
        (?:public|protected|private|static|final|abstract|sealed|non-sealed)\s+(?:class|interface|enum|record)\s+\w+
    )/mgx
) {
    my $match = $&;
    my $pos = pos() - length($match);
    my $prefix = substr($_, 0, $pos);
    my $line = 1 + ($prefix =~ tr/\n//);

    if ($prefix !~ /\/\*\*[^*]*\*+(?:[^\/\*][^*]*\*+)*\/\s*(?:@[\w$.()\"", =]+\s*)*$/s) {
        print "$ARGV:$line\n";
    }
}
PERL

missing_output="$(perl -0ne "$PERL_CHECK" "${java_files[@]}" | sort -u)"

if [ -n "$missing_output" ]; then
    echo "Faltan bloques Javadoc antes de estas declaraciones:"
    echo "$missing_output"
    exit 1
fi

echo "Javadoc OK: no se detectaron declaraciones sin bloque Javadoc en ${#java_files[@]} archivos."
