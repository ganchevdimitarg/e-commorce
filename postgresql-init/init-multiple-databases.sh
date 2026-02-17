#!/bin/bash
set -e

databases=(
    "orders"
    "registered_client"
    "notification"
    "payments"
    "catalog"
)

for db in "${databases[@]}"; do
    echo "Creating database: $db"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE "$db";
EOSQL
done
