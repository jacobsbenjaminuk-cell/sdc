#!/bin/sh

# OpenTelemetry Agent Configuration
OTEL_AGENT_PATH="$JETTY_BASE/otel/opentelemetry-javaagent.jar"
OTEL_OPTS=""

if [ -f "$OTEL_AGENT_PATH" ] && [ "${OTEL_ENABLED:-false}" = "true" ]; then
    OTEL_OPTS="-javaagent:$OTEL_AGENT_PATH"
    echo "OpenTelemetry agent enabled"
fi

JAVA_OPTIONS="$JAVA_OPTIONS \
            -Dcom.datastax.driver.USE_NATIVE_CLOCK=false \
            -Dconfig.home=$JETTY_BASE/config \
	    -Duser.dir=$JETTY_BASE \
            -Dlog.home=$JETTY_BASE/logs \
            -Dlogback.configurationFile=$JETTY_BASE/config/onboarding-be/logback.xml \
            -Dconfiguration.yaml=$JETTY_BASE/config/onboarding-be/onboarding_configuration.yaml \
            -Dfeatures.properties=$JETTY_BASE/config/onboarding-be/features.properties \
            -XX:+HeapDumpOnOutOfMemoryError \
            -Dconfig.location=$JETTY_BASE/config/onboarding-be/."

# TLS keystore: mount one at $JETTY_BASE/$KEYSTORE_PATH and set KEYSTORE_PASSWORD, otherwise a
# self-signed keystore with a random password is generated for this container.
KEYSTORE_PATH="${KEYSTORE_PATH:-etc/org.onap.sdc.p12}"
if [ ! -f "$JETTY_BASE/$KEYSTORE_PATH" ]; then
    KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')}"
    keytool -genkeypair -noprompt -alias jetty -keyalg RSA -keysize 2048 -validity 365 \
        -dname "CN=$(hostname)" -storetype PKCS12 \
        -keystore "$JETTY_BASE/$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" || exit 1
    chmod 600 "$JETTY_BASE/$KEYSTORE_PATH"
fi
set --
if [ -n "$KEYSTORE_PASSWORD" ]; then
    set -- \
        "jetty.sslContext.keyStorePath=$KEYSTORE_PATH" \
        "jetty.sslContext.keyStorePassword=$KEYSTORE_PASSWORD" \
        "jetty.sslContext.keyManagerPassword=$KEYSTORE_PASSWORD" \
        "jetty.sslContext.trustStorePath=${TRUSTSTORE_PATH:-$KEYSTORE_PATH}" \
        "jetty.sslContext.trustStorePassword=${TRUSTSTORE_PASSWORD:-$KEYSTORE_PASSWORD}"
fi

cd $JETTY_HOME
echo "jetty.httpConfig.sendServerVersion=false" >> $JETTY_HOME/start.d/start.ini

java $OTEL_OPTS $JAVA_OPTIONS -jar "${JETTY_HOME}/start.jar" "$@"
