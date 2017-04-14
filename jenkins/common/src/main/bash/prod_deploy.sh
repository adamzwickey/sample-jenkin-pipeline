#!/bin/bash

set -ex

appName=${PROJECT_ARTIFACT_ID}
logInToCf "${cfUsername}" "${cfPassword}" "${CF_PROD_ORG}" "${CF_PROD_SPACE}" "${CF_PROD_API_URL}"
set +e
appExist ${appName}
APP_EXIST=$?
appExist "${appName}-new"
APP_GREEN_EXIST=$?
set -e
if [ ${APP_EXIST} -eq 1 ]; then
  #IF app does not exist do free deploy prod
  deployAppToPCF "${PROJECT_ARTIFACT_ID}" 'prod' "${CF_PROD_ORG}" "${CF_PROD_SPACE}" "${CF_PROD_API_URL}" "${PROD_DOMAIN}" "${PROD_INSTANCES}"
  exit 0
elif [ ${APP_GREEN_EXIST} -eq 0 ]; then
  echo "Deployment another new version is in the process. No new production blue/green deployment"
  exit 1
else
  #Doing blue green deployment
  deployAppToPCF "${PROJECT_ARTIFACT_ID}-new" 'prod' "${CF_PROD_ORG}" "${CF_PROD_SPACE}" "${CF_PROD_API_URL}" "${PROD_DOMAIN}" 1
  cf map-route "${appName}-new" "${PROD_DOMAIN}" -n ${appName}
fi
