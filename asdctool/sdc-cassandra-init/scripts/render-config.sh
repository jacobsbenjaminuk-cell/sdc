#!/bin/sh

# Renders the sdctool configuration templates with the Cassandra application
# credentials taken from the environment (SDC_USER / SDC_PASSWORD).

set -e

. /home/sdc/scripts/cassandra-env.sh

CONFIG_DIR=/home/sdc/sdctool/config
mkdir -p "$CONFIG_DIR"

render() {
    awk -v mode="$3" '
        function replace_all(s, from, to,    out, p) {
            out = ""
            while ((p = index(s, from)) > 0) {
                out = out substr(s, 1, p - 1) to
                s = substr(s, p + length(from))
            }
            return out s
        }
        function escape(v) {
            if (mode == "yaml") return replace_all(v, "\047", "\047\047")
            return replace_all(v, "\\", "\\\\")
        }
        BEGIN {
            user = escape(ENVIRON["SDC_USER"])
            pass = escape(ENVIRON["SDC_PASSWORD"])
        }
        {
            line = replace_all($0, "@SDC_USER@", user)
            print replace_all(line, "@SDC_PASSWORD@", pass)
        }' "$1" > "$2"
}

render /home/sdc/scripts/configuration.yaml "$CONFIG_DIR/configuration.yaml" yaml
render /home/sdc/scripts/janusgraph.properties "$CONFIG_DIR/janusgraph.properties" properties
