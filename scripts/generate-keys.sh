#!/usr/bin/env bash
# Generates the RSA keypair identity-service signs access tokens with.
#
# Optional: without it, identity-service generates an ephemeral keypair at startup and
# warns about it. Use this when you want tokens to survive a restart, or when running more
# than one identity replica (they must agree on the key).
set -euo pipefail

KEY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/infra/keys"
mkdir -p "$KEY_DIR"

if [[ -f "$KEY_DIR/jwt-private.pem" ]]; then
    echo "Keys already exist at $KEY_DIR -- refusing to overwrite."
    echo "Delete them first if you really want to rotate (this invalidates every issued token)."
    exit 1
fi

# PKCS#8 is what Spring Security's RsaKeyConverters.pkcs8() expects; the traditional
# PKCS#1 "BEGIN RSA PRIVATE KEY" format will not parse.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEY_DIR/jwt-private.pem"
openssl rsa -in "$KEY_DIR/jwt-private.pem" -pubout -out "$KEY_DIR/jwt-public.pem"

chmod 600 "$KEY_DIR/jwt-private.pem"

echo "Wrote $KEY_DIR/jwt-private.pem and jwt-public.pem"
echo
echo "Now add these to your .env file:"
echo "  JWT_PRIVATE_KEY_LOCATION=file:/run/keys/jwt-private.pem"
echo "  JWT_PUBLIC_KEY_LOCATION=file:/run/keys/jwt-public.pem"
