#!/bin/bash

set -o errexit

__DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[[ -f "${__DIR}/pipeline.sh" ]] && source "${__DIR}/pipeline.sh" || \
    echo "No pipeline.sh found"

buildGradleProperties

./gradlew clean build upload --stacktrace

result=$( ./gradlew printVersion -q)
version=$( echo "${result}" | tail -1 )
projectGroupId=$( retrieveGroupId )
projectArtifactId=$( retrieveArtifactId )
echo PIPELINE_VERSION=${version} >> my_env.properties
echo PROJECT_GROUP_ID=${projectGroupId} >> my_env.properties
echo PROJECT_ARTIFACT_ID=${projectArtifactId} >> my_env.properties
cat my_env.properties
