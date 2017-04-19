#!/bin/bash

if [[ $# < 2 ]] ; then
    echo "You have to pass three params"
    echo "1 - git username with access to the forked repos"
    echo "2 - org where the forked repos lay"
    echo " - (optional) external ip (for example Docker Machine if you're using one)"
    echo "Example: ./start.sh user 192.168.99.100"
    exit 0
fi

export PIPELINE_GIT_USERNAME="${1}"
export EXTERNAL_IP="${2}"

if [[ -z "${EXTERNAL_IP}" ]]; then
    EXTERNAL_IP=`echo ${DOCKER_HOST} | cut -d ":" -f 2 | cut -d "/" -f 3`
    if [[ -z "${EXTERNAL_IP}" ]]; then
        EXTERNAL_IP="$( ./whats_my_ip.sh )"
    fi
fi

echo "External IP [${EXTERNAL_IP}]"

docker-compose build
docker-compose up -d
