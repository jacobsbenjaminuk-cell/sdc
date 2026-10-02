#!/bin/sh

# OpenTelemetry Agent Configuration
OTEL_AGENT_PATH="$JETTY_BASE/otel/opentelemetry-javaagent.jar"
OTEL_OPTS=""

if [ -f "$OTEL_AGENT_PATH" ] && [ "${OTEL_ENABLED:-false}" = "true" ]; then
    OTEL_OPTS="-javaagent:$OTEL_AGENT_PATH"
    echo "OpenTelemetry agent enabled"
fi

CONFIG_YAML="$JETTY_BASE/config/catalog-fe/configuration.yaml"
BASIC_AUTH_ENABLED="${BASIC_AUTH_ENABLED:-true}"

case "$BASIC_AUTH_ENABLED" in
    true|false) ;;
    *) echo "BASIC_AUTH_ENABLED must be 'true' or 'false'" >&2; exit 1 ;;
esac

NEWLINE='
'

yaml_single_quote_escape() {
    printf '%s' "$1" | sed "s/'/''/g"
}

if grep -q '${BASIC_AUTH_' "$CONFIG_YAML"; then
    if [ "$BASIC_AUTH_ENABLED" = "true" ] && { [ -z "$BASIC_AUTH_USERNAME" ] || [ -z "$BASIC_AUTH_PASSWORD" ]; }; then
        echo "BASIC_AUTH_USERNAME and BASIC_AUTH_PASSWORD must be set when BASIC_AUTH_ENABLED is true" >&2
        exit 1
    fi
    case "$BASIC_AUTH_USERNAME$BASIC_AUTH_PASSWORD" in
        *"$NEWLINE"*) echo "BASIC_AUTH_USERNAME and BASIC_AUTH_PASSWORD must not contain newlines" >&2; exit 1 ;;
    esac
    ( umask 077 && \
      BASIC_AUTH_USERNAME="$(yaml_single_quote_escape "$BASIC_AUTH_USERNAME")" \
      BASIC_AUTH_PASSWORD="$(yaml_single_quote_escape "$BASIC_AUTH_PASSWORD")" \
      BASIC_AUTH_ENABLED="$BASIC_AUTH_ENABLED" \
      envsubst '${BASIC_AUTH_ENABLED} ${BASIC_AUTH_USERNAME} ${BASIC_AUTH_PASSWORD}' \
        < "$CONFIG_YAML" > "$CONFIG_YAML.tmp" ) && \
    mv -f "$CONFIG_YAML.tmp" "$CONFIG_YAML" || { echo "Failed to apply basic auth settings to $CONFIG_YAML" >&2; exit 1; }
fi

JAVA_OPTIONS="$JAVA_OPTIONS \
               -Dconfig.home=$JETTY_BASE/config \
               -Dlog.home=$JETTY_BASE/logs \
               -Dlogback.configurationFile=$JETTY_BASE/config/catalog-fe/logback.xml \
               -Dconfiguration.yaml=$JETTY_BASE/config/catalog-fe/configuration.yaml \
               -Donboarding_configuration.yaml=$JETTY_BASE/config/onboarding-fe/onboarding_configuration.yaml \
               -Djavax.net.ssl.trustStore=$JETTY_BASE/etc/org.onap.sdc.trust.jks"

if [ -n "$FE_TRUSTSTORE_PASSWORD" ]; then
    JAVA_OPTIONS="$JAVA_OPTIONS -Djavax.net.ssl.trustStorePassword=$FE_TRUSTSTORE_PASSWORD"
fi

cd $JETTY_HOME

java $OTEL_OPTS $JAVA_OPTIONS -jar "${JETTY_HOME}/start.jar"
