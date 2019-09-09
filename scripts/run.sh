#!/bin/bash
# export STORAGE_BUCKET="clingen-dev-kafka-audit"
# export BOOTSTRAP_SERVERS="10.142.0.3:9093"
export MOUNT_POINT="/home/clingen/mnt"
export KEYFILE="/storage-bucket-keyfile.json"
export PERSISTER_GROUP="persister_stage"
export PERSISTER_TRUSTSTORE_LOCATION="keys/dev.persister.client.keystore.jks"
export PERSISTER_KEYSTORE_LOCATION="keys/dev.persister.client.keystore.jks"
export JAVA_OPTS=""
# export JAVA_OPTS="-Djavax.net.debug=ssl"

err=0
if [ -x $STORAGE_BUCKET ]; then
    echo "Environment variable STORAGE_BUCKET must be defined in GCE VM setup."
    err=1
fi
	
if [ -x $BOOTSTRAP_SERVERS ]; then
    echo "Environment variable BOOTSTRAP_SERVERS must be defined in GCE VM setup."
    err=1
fi

if [ -x $PERSISTER_TRUSTSTORE_PASSWORD ]; then
    echo "Environment variable PERSISTER_TRUSTSTORE_PASSWORD must be defined in GCE VM setup."
    err=1
fi

if [ -x $PERSISTER_KEYSTORE_PASSWORD ]; then
    echo "Environment variable PERSISTER_KEYSTORE_PASSWORD must be defined in GCE VM setup."
    err=1
fi

if [ -x $PERSISTER_KEY_PASSWORD ]; then
    echo "Environment variable STORAGE_BUCKETPERSISTER_KEY_PASSWORD must be defined in GCE VM setup."
    err=1
fi

if [ $err == 1 ];then
    echo "Exiting."
    exit 1
fi

if [ ! -d $MOUNT_POINT ]
then
    mkdir $MOUNT_POINT
fi
/usr/local/bin/gcsfuse -key-file $KEYFILE $STORAGE_BUCKET $MOUNT_POINT
java $JAVA_OPTS -jar persister-0.1.0.jar -o $MOUNT_POINT
exec /bin/bash
