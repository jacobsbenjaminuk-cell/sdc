#!/bin/sh
# Renders the Cassandra config template into place, filling the encryption
# settings from the environment so no keystore passwords live in the image.
#
#   CS_CLIENT_ENCRYPTION_ENABLED  true|false (default false)
#   CS_INTERNODE_ENCRYPTION       none|all|dc|rack (default none)
#   CS_KEYSTORE_PATH              default /etc/cassandra/cs_trust/.keystore
#   CS_KEYSTORE_PASSWORD          required when any encryption is enabled
#   CS_TRUSTSTORE_PATH            default /etc/cassandra/cs_trust/.truststore
#   CS_TRUSTSTORE_PASSWORD        required when internode encryption is enabled

src=${1:-/root/scripts/cassandra.yaml}
dest=${2:-/etc/cassandra/cassandra.yaml}

CS_CLIENT_ENCRYPTION_ENABLED=${CS_CLIENT_ENCRYPTION_ENABLED:-false}
CS_INTERNODE_ENCRYPTION=${CS_INTERNODE_ENCRYPTION:-none}
CS_KEYSTORE_PATH=${CS_KEYSTORE_PATH:-/etc/cassandra/cs_trust/.keystore}
CS_KEYSTORE_PASSWORD=${CS_KEYSTORE_PASSWORD:-}
CS_TRUSTSTORE_PATH=${CS_TRUSTSTORE_PATH:-/etc/cassandra/cs_trust/.truststore}
CS_TRUSTSTORE_PASSWORD=${CS_TRUSTSTORE_PASSWORD:-}
export CS_CLIENT_ENCRYPTION_ENABLED CS_INTERNODE_ENCRYPTION CS_KEYSTORE_PATH \
    CS_KEYSTORE_PASSWORD CS_TRUSTSTORE_PATH CS_TRUSTSTORE_PASSWORD

fail() {
    echo "render_cassandra_yaml: $1" >&2
    exit 1
}

case "$CS_CLIENT_ENCRYPTION_ENABLED" in
    true|false) ;;
    *) fail "CS_CLIENT_ENCRYPTION_ENABLED must be true or false" ;;
esac

case "$CS_INTERNODE_ENCRYPTION" in
    none|all|dc|rack) ;;
    *) fail "CS_INTERNODE_ENCRYPTION must be one of none, all, dc, rack" ;;
esac

if [ "$CS_CLIENT_ENCRYPTION_ENABLED" = "true" ] || [ "$CS_INTERNODE_ENCRYPTION" != "none" ]; then
    [ -n "$CS_KEYSTORE_PASSWORD" ] || fail "CS_KEYSTORE_PASSWORD is required when encryption is enabled"
    [ -r "$CS_KEYSTORE_PATH" ] || fail "keystore $CS_KEYSTORE_PATH is not readable"
fi

if [ "$CS_INTERNODE_ENCRYPTION" != "none" ]; then
    [ -n "$CS_TRUSTSTORE_PASSWORD" ] || fail "CS_TRUSTSTORE_PASSWORD is required when internode encryption is enabled"
    [ -r "$CS_TRUSTSTORE_PATH" ] || fail "truststore $CS_TRUSTSTORE_PATH is not readable"
fi

awk '
function q(v) {
    gsub(/\047/, "\047\047", v)
    return "\047" v "\047"
}
/^[A-Za-z_]+:/ {
    section = $0
    sub(/:.*/, "", section)
}
section == "server_encryption_options" && /^    internode_encryption:/ { print "    internode_encryption: " ENVIRON["CS_INTERNODE_ENCRYPTION"]; next }
section == "server_encryption_options" && /^    truststore:/ { print "    truststore: " q(ENVIRON["CS_TRUSTSTORE_PATH"]); next }
section == "server_encryption_options" && /^    truststore_password:/ { print "    truststore_password: " q(ENVIRON["CS_TRUSTSTORE_PASSWORD"]); next }
section == "client_encryption_options" && /^    enabled:/ { print "    enabled: " ENVIRON["CS_CLIENT_ENCRYPTION_ENABLED"]; next }
(section == "server_encryption_options" || section == "client_encryption_options") && /^    keystore:/ { print "    keystore: " q(ENVIRON["CS_KEYSTORE_PATH"]); next }
(section == "server_encryption_options" || section == "client_encryption_options") && /^    keystore_password:/ { print "    keystore_password: " q(ENVIRON["CS_KEYSTORE_PASSWORD"]); next }
{ print }
' "$src" > "$dest.tmp" || fail "failed to render $src"

chmod 640 "$dest.tmp" && mv "$dest.tmp" "$dest" || fail "failed to install $dest"
