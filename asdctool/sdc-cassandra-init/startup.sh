#!/bin/sh

cd /home/sdc/scripts
sh /home/sdc/scripts/render-config.sh || exit 1
sh conditional_test.sh
sh /home/sdc/scripts/change_cassandra_user.sh
mkdir -p /tmp/config
sh /home/sdc/scripts/create_dox_keyspace.sh
cd /home/sdc/tools/build/scripts
sh /home/sdc/tools/build/scripts/onboard-db-schema-creation.sh
cd /home/sdc/scripts
sh /home/sdc/scripts/create-alter-dox-db.sh
cd /home/sdc/sdctool/scripts
sh /home/sdc/sdctool/scripts/schemaCreation.sh /home/sdc/sdctool/config
sh /home/sdc/sdctool/scripts/janusGraphSchemaCreation.sh /home/sdc/sdctool/config
cd /home/sdc/scripts
sh /home/sdc/scripts/importconformance.sh