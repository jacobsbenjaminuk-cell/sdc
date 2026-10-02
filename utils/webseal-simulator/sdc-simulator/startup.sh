#!/bin/sh

# Set defaults for environment variables
export FE_URL="${FE_URL:-http://localhost:8181}"
export PERMITTED_ANCESTORS="${PERMITTED_ANCESTORS:-}"

# Generate webseal.conf from template using envsubst
envsubst '${FE_URL} ${PERMITTED_ANCESTORS}' \
  < "$JETTY_BASE/config/sdc-simulator/webseal.conf.tpl" \
  > "$JETTY_BASE/config/sdc-simulator/webseal.conf"

JAVA_OPTIONS=" $JAVA_OPTIONS \
		-Xdebug -agentlib:jdwp=transport=dt_socket,address=*:5000,server=y,suspend=n -Xmx128m -Xms128m -Xss1m \
  -Dconfig.home=$JETTY_BASE/config/sdc-simulator \
  -Dlog.home=$JETTY_BASE/logs \
  -Dlogback.configurationFile=$JETTY_BASE/config/sdc-simulator/logback.xml \
  -Djavax.net.ssl.trustStore=$JETTY_BASE/etc/org.onap.sdc.trust.jks \
  -Djavax.net.ssl.trustStorePassword=z+KEj;t+,KN^iimSiS89e#p0"

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
echo "etc/rewrite-root-to-sdc1.xml" >> $JETTY_HOME/start.d/rewrite.ini
echo "jetty.httpConfig.sendServerVersion=false" >> $JETTY_HOME/start.d/start.ini

java $JAVA_OPTIONS -jar "${JETTY_HOME}/start.jar" "$@"
