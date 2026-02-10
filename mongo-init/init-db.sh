#!/bin/bash
set -e

echo "Creating application user..."

mongosh admin \
  -u "$MONGO_INITDB_ROOT_USERNAME" \
  -p "$MONGO_INITDB_ROOT_PASSWORD" <<EOF
db = db.getSiblingDB('$MONGO_DB_DATABASE')
db.createUser({
  user: "$MONGO_DB_USERNAME",
  pwd: "$MONGO_DB_PASSWORD",
  roles: [{ role: "readWrite", db: "$MONGO_DB_DATABASE" }]
})
EOF
