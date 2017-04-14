#!/bin/bash

set -e

logInToCf "${cfUsername}" "${cfPassword}" "${CF_TEST_ORG}" "${CF_TEST_SPACE}" "${CF_TEST_API_URL}"
deployAppToPCF "${PROJECT_ARTIFACT_ID}" 'test' "${CF_TEST_ORG}" "${CF_TEST_SPACE}" "${CF_TEST_API_URL}" "${TEST_DOMAIN}"
