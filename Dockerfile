FROM golang:1.10.0-alpine
RUN apk add --no-cache git
ENV GOPATH /go
RUN go get -u github.com/googlecloudplatform/gcsfuse

FROM frolvlad/alpine-python3

RUN apk add --update \
    git \
    curl \
    which \
    bash \
    fuse \
    openjdk8 

COPY --from=0 /go/bin/gcsfuse /usr/local/bin

## gcloud setup
RUN curl -sSL https://sdk.cloud.google.com | /bin/bash
ENV PATH $PATH:/root/google-cloud-sdk/bin

# !!! Service Acount key file UPDATE HERE!!!!
# COPY resources/SERVICE_ACCOUNT_KEY.JSON /storage-bucket-keyfile.json
COPY resources/clingen-dev-e9cd14dbbf39.json /storage-bucket-keyfile.json

RUN gcloud auth activate-service-account --key-file /storage-bucket-keyfile.json
RUN gcloud config set project clingen-dev

ENV ZOOKEEPER_VERSION 3.4.13
ENV ZOOKEEPER_HOME /opt/zookeeper-"$ZOOKEEPER_VERSION"

RUN wget -q http://archive.apache.org/dist/zookeeper/zookeeper-"$ZOOKEEPER_VERSION"/zookeeper-"$ZOOKEEPER_VERSION".tar.gz -O /tmp/zookeeper-"$ZOOKEEPER_VERSION".tgz
RUN ls -l /tmp/zookeeper-"$ZOOKEEPER_VERSION".tgz
RUN tar xfz /tmp/zookeeper-"$ZOOKEEPER_VERSION".tgz -C /opt && rm /tmp/zookeeper-"$ZOOKEEPER_VERSION".tgz

ENV SCALA_VERSION 2.12
ENV KAFKA_VERSION 2.3.0
ENV KAFKA_HOME /opt/kafka_"$SCALA_VERSION"-"$KAFKA_VERSION"
ENV KAFKA_DOWNLOAD_URL https://archive.apache.org/dist/kafka/"$KAFKA_VERSION"/kafka_"$SCALA_VERSION"-"$KAFKA_VERSION".tgz

RUN wget -q $KAFKA_DOWNLOAD_URL -O /tmp/kafka_"$SCALA_VERSION"-"$KAFKA_VERSION".tgz
RUN tar xfz /tmp/kafka_"$SCALA_VERSION"-"$KAFKA_VERSION".tgz -C /opt && rm /tmp/kafka_"$SCALA_VERSION"-"$KAFKA_VERSION".tgz

ENV PATH $PATH:$KAFKA_HOME/bin:$ZOOKEEPER_HOME/bin

RUN addgroup clingen
RUN adduser -h /home/clingen -s /bin/bash -G clingen -g "Clingen User" -D clingen

WORKDIR /home/clingen
# USER clingen

ADD keys keys
ADD target/uberjar/persister-0.1.0-SNAPSHOT-standalone.jar persister-0.1.0.jar
ADD scripts/run.sh run.sh

RUN mkdir mnt
CMD ["./run.sh"]
