#!/bin/sh

# Source the environment file
. /home/sdc/scripts/cassandra-env.sh  # Replace with the actual path to your env file

pass_changed=99
retry_num=1
is_up=0
while [ $is_up -eq 0 -a $retry_num -le 100 ]; do
   echo "exit" | cqlsh -u cassandra -p $CS_PASSWORD $CASSANDRA_IP $CASSANDRA_PORT --cqlversion="$cqlversion"
   res1=$?
   if [ $res1 -eq 0 ]; then
      echo "`date` --- cqlsh is able to connect."
      is_up=1
   else
      echo "`date` --- cqlsh is NOT able to connect yet. sleep 5"
      sleep 5
   fi
   retry_num=$((retry_num+1))
done

cassandra_user_exist=$(echo "list users;" | cqlsh -u cassandra -p $CS_PASSWORD $CASSANDRA_IP $CASSANDRA_PORT --cqlversion="$cqlversion" | grep -c $SDC_USER)
if [ $cassandra_user_exist -eq 1 ]; then
    echo "Cassandra user $SDC_USER already exists"
else
    echo "Going to create $SDC_USER"
    echo "create user $SDC_USER with password '$SDC_PASSWORD' nosuperuser;" | cqlsh -u cassandra -p $CS_PASSWORD $CASSANDRA_IP $CASSANDRA_PORT --cqlversion="$cqlversion"
fi

CQLSH_ADMIN="cqlsh -u cassandra -p $CS_PASSWORD $CASSANDRA_IP $CASSANDRA_PORT --cqlversion=$cqlversion"

echo "Granting $SDC_USER permission to create keyspaces"
echo "grant create on all keyspaces to $SDC_USER;" | $CQLSH_ADMIN || exit 1

existing_keyspaces=$(echo "select keyspace_name from system_schema.keyspaces;" | $CQLSH_ADMIN | tr -d ' \r')
for keyspace in dox zusammen_dox sdcaudit sdcartifact sdccomponent sdcrepository sdctitan; do
    if echo "$existing_keyspaces" | grep -qx "$keyspace"; then
        echo "Granting $SDC_USER all permissions on existing keyspace $keyspace"
        echo "grant all permissions on keyspace $keyspace to $SDC_USER;" | $CQLSH_ADMIN || exit 1
    fi
done
