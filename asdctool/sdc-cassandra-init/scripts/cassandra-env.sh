#!/bin/sh

# Export other necessary variables
export CASSANDRA_IP="SDC-CS"
export CS_PORT=9042
export SDC_USER="${SDC_USER:?SDC_USER must be set}"
export SDC_PASSWORD="${SDC_PASSWORD:?SDC_PASSWORD must be set}"
export CASSANDRA_PASS="${CS_PASSWORD:?CS_PASSWORD must be set}"
case "$SDC_USER" in
    ''|[!A-Za-z_]*|*[!A-Za-z0-9_]*)
        echo "SDC_USER must contain only letters, digits and underscores" >&2
        exit 1
        ;;
    [Cc][Aa][Ss][Ss][Aa][Nn][Dd][Rr][Aa])
        echo "SDC_USER must not be the Cassandra superuser" >&2
        exit 1
        ;;
esac
export DC_NAME="SDC-CS-integration-test"
export cqlversion="3.4.4"
export DISABLE_HTTP="false"
