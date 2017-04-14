#!/bin/bash

set -e

logInToCf "${cfUsername}" "${cfPassword}" "${CF_STAGE_ORG}" "${CF_STAGE_SPACE}" "${CF_STAGE_API_URL}"
deployAppToPCF "${PROJECT_ARTIFACT_ID}" 'stage' "${CF_STAGE_ORG}" "${CF_STAGE_SPACE}" "${CF_STAGE_API_URL}" "${STAGE_DOMAIN}"
