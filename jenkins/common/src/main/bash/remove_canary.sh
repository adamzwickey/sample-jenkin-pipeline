set -e


appName=${PROJECT_ARTIFACT_ID}
logInToCf "${cfUsername}" "${cfPassword}" "${CF_PROD_ORG}" "${CF_PROD_SPACE}" "${CF_PROD_API_URL}"
cf delete "${appName}-new" -f
