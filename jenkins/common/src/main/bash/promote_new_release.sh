#!/bin/bash


#This will decommison the old release application and promote the canary instance

set -ex

appName=${PROJECT_ARTIFACT_ID}
logInToCf "${cfUsername}" "${cfPassword}" "${CF_PROD_ORG}" "${CF_PROD_SPACE}" "${CF_PROD_API_URL}"
set +e
appExist ${appName}
APP_EXIST=$?
appExist "${appName}-new"
APP_GREEN_EXIST=$?
set -e
if [ ${APP_GREEN_EXIST} -eq 1 ]; then
  echo "No action required, since canary instances not exists"
else
  cf scale "${appName}-new" -i "${PROD_INSTANCES}"
  cf unmap-route "${appName}" "${PROD_DOMAIN}" -n "${appName}"
  cf unmap-route "${appName}-new" "${PROD_DOMAIN}" -n "${appName}-new"
  cf delete -f "${appName}"
  cf rename "${appName}-new" "${appName}"
fi
