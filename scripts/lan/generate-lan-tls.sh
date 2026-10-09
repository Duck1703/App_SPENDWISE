#!/usr/bin/env bash
# M21 - generate the private LAN CA + server certificate (Level B HTTPS).
#
# Run once on the backend host (Git Bash). Produces in ~/.spendwise-lan-tls/:
#   ca-key.pem         CA private key   - Human-controlled, never committed
#   ca-cert.pem        CA public cert   - copy to app/src/main/res/raw/spendwise_lan_ca.pem
#   server-key.pem     server private key - never committed
#   server-cert.pem    server cert (SAN = LAN IP + localhost + hostname)
#   server-keystore.p12  PKCS12 bundle Spring Boot serves via SERVER_SSL_KEY_STORE
#   lan-tls.env        keystore path + generated password (chmod 600)
#
# Re-run when the LAN IP changes or the cert nears expiry (825-day validity).
set -euo pipefail

LAN_IP="${1:?usage: generate-lan-tls.sh <LAN_IP>  e.g. 192.168.1.236}"
HOSTNAME_OVERRIDE="${2:-}"
OUT="$HOME/.spendwise-lan-tls"
mkdir -p "$OUT"; chmod 700 "$OUT"
cd "$OUT"

export MSYS_NO_PATHCONV=1
HOSTNAME_VAL="${HOSTNAME_OVERRIDE:-$(hostname)}"

# 1. Private CA (10y). Public cert is safe to commit; the KEY never is.
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca-cert.pem \
    -days 3650 -nodes \
    -subj "/CN=Spendwise LAN CA/O=Spendwise Personal" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"

# 2. Server cert (825d - Apple-anchored max trust horizon for issued certs).
openssl req -newkey rsa:2048 -keyout server-key.pem -out server.csr -nodes \
    -subj "/CN=${HOSTNAME_VAL}/O=Spendwise Personal"
printf '[v3_req]\nbasicConstraints=CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=IP:%s,IP:127.0.0.1,DNS:localhost,DNS:%s\n' \
    "$LAN_IP" "$HOSTNAME_VAL" > server-ext.cnf
openssl x509 -req -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days 825 -extensions v3_req \
    -extfile server-ext.cnf

# 3. PKCS12 bundle for Spring Boot (server.ssl.key-store).
KS_PASS="$(openssl rand -hex 16)"
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -certfile ca-cert.pem -name spendwise-lan -out server-keystore.p12 \
    -passout "pass:$KS_PASS"

printf 'SPENDWISE_TLS_KEYSTORE=%s\nSPENDWISE_TLS_STORE_PASSWORD=%s\n' \
    "$(cygpath -w "$OUT/server-keystore.p12" 2>/dev/null || echo "$OUT/server-keystore.p12")" \
    "$KS_PASS" > lan-tls.env
chmod 600 lan-tls.env ca-key.pem server-key.pem server-keystore.p12

echo "Generated into $OUT"
echo "Copy the PUBLIC CA cert into the app:  cp ca-cert.pem <repo>/app/src/main/res/raw/spendwise_lan_ca.pem"
echo "Keystore path + password are in lan-tls.env (chmod 600, never committed)."
