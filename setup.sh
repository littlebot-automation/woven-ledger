#!/usr/bin/env bash
#
# Woven Ledger — finish the build.
#
# All application source is already in place. This installs the PHP dependencies,
# lints, creates the SQLite schema, loads demo data and tells you how to start.
#
# Run from the project root:   bash setup.sh
#
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> 1/5  Installing dependencies (Symfony 7.3, EasyAdmin 4, Doctrine ORM 3)"
composer install --no-interaction --no-progress

echo "==> 2/5  Linting every PHP file"
fail=0
while IFS= read -r f; do
    php -l "$f" > /dev/null || { echo "    SYNTAX ERROR: $f"; fail=1; }
done < <(find src -name '*.php')
if [ "$fail" -ne 0 ]; then
    echo "    Fix the syntax errors above before continuing."
    exit 1
fi
echo "    all files parse cleanly"

echo "==> 3/5  Creating the SQLite schema at var/data.db"
mkdir -p var
rm -f var/data.db
php bin/console doctrine:schema:create --no-interaction

echo "==> 4/5  Loading demo data"
php bin/console doctrine:fixtures:load --no-interaction

echo "==> 5/5  Warming the cache"
php bin/console cache:clear

cat <<'EOF'

------------------------------------------------------------------
Woven Ledger is ready.

Start it with:      symfony server:start -d
             or:    php -S 127.0.0.1:8000 -t public

Then open:          http://127.0.0.1:8000/     (redirects to /admin)
------------------------------------------------------------------
EOF
