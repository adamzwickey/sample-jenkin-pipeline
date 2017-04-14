#!/bin/bash
set -e

function logInToCf() {
    local cfUsername="${1}"
    local cfPassword="${2}"
    local cfOrg="${3}"
    local cfSpace="${4}"
    local apiUrl="${5}"
    echo "Cloud foundry version"
    cf --version

    echo "Logging in to CF to org [${cfOrg}], space [${cfSpace}]"
    cf api --skip-ssl-validation "${apiUrl}"
    cf login -u "${cfUsername}" -p "${cfPassword}" -o "${cfOrg}" -s "${cfSpace}"
}

function appHost() {
    local appName="${1}"
    local lowerCase="$( echo "${appName}" | tr '[:upper:]' '[:lower:]' )"
    local APP_HOST=`cf apps | awk -v "app=${lowerCase}" '$1 == app {print($0)}' | tr -s ' ' | cut -d' ' -f 6 | cut -d, -f1`
    echo "${APP_HOST}" | tail -1
}

function deployAppWithName() {
    local appName="${1}"
    local jarName="${2}"
    local env="${3}"
    local domain="${4}"
    local instances="${5}"
    local lowerCaseAppName=$( echo "${appName}" | tr '[:upper:]' '[:lower:]' )
    local hostname="${lowerCaseAppName}"

    if [ "${env}" != "prod" ]; then
      hostname="${hostname}-${env}"
    fi

    echo "Deploying app with name [${lowerCaseAppName}], env [${env}] and host [${hostname}]"
    if [ "${instances}" == "" ]; then
      cf push "${lowerCaseAppName}" -p "${OUTPUT_FOLDER}/${jarName}.jar" -n "${hostname}" -d "${domain}" --no-start
    else
      cf push "${lowerCaseAppName}" -p "${OUTPUT_FOLDER}/${jarName}.jar" -n "${hostname}" -d "${domain}" -i ${instances} --no-start
    fi
    APPLICATION_DOMAIN="$( appHost ${lowerCaseAppName} )"
    echo "Determined that application_domain for [${lowerCaseAppName}] is [${APPLICATION_DOMAIN}]"
}

function restartApp() {
    local appName="${1}"
    echo "Restarting app with name [${appName}]"
    cf restart "${appName}"
}

function restageApp() {
    local appName="${1}"
    echo "Restage app with name [${appName}]"
    cf restage "${appName}"
}

function startApp() {
    local appName="${1}"
    echo "starting app with name [${appName}]"
    cf start "${appName}"
}

# The values of group / artifact ids can be later retrieved from Maven
function downloadJar() {
    local redownloadInfra="${1}"
    local repoWithJars="${2}"
    local groupId="${3}"
    local artifactId="${4}"
    local version="${5}"
    local destination="`pwd`/${OUTPUT_FOLDER}/${artifactId}-${version}.jar"
    local changedGroupId="$( echo "${groupId}" | tr . / )"
    local pathToJar="${repoWithJars}/${changedGroupId}/${artifactId}/${version}/${artifactId}-${version}.jar"
    if [[ ! -e ${destination} || ( -e ${destination} && ${redownloadInfra} == "true" ) ]]; then
        mkdir -p "${OUTPUT_FOLDER}"
        echo "Current folder is [`pwd`]; Downloading [${pathToJar}] to [${destination}]"
        (curl "${pathToJar}" -o "${destination}" --fail && echo "File downloaded successfully!") || (echo "Failed to download file!" && return 1)
    else
        echo "File [${destination}] exists and redownload flag was set to false. Will not download it again"
    fi
}

function retrieveGroupId() {
    local result=$( ./gradlew groupId -q )
    result=$( echo "${result}" | tail -1 )
    echo "${result}"
}

function retrieveArtifactId() {
    local result=$( ./gradlew artifactId -q )
    result=$( echo "${result}" | tail -1 )
    echo "${result}"
}

function isMavenProject() {
    [ -f "mvnw" ]
}

function isGradleProject() {
    [ -f "gradlew" ]
}

function projectType() {
    echo "GRADLE"
}

function outputFolder() {
    echo "build/libs"
}

function testResultsFolder() {
    echo "**/test-results/*.xml"
}

function printTestResults() {
    echo -e "\n\nBuild failed!!! - will print all test results to the console (it's the easiest way to debug anything later)\n\n" && tail -n +1 "$( testResultsFolder )"
}

function createServices() {
}

function createJaveOPTs() {
  local appName="${1}"
  local env="${2}"
  JAVA_OPTS=""
  echo ${JAVA_OPTS}
}

function deployAppToPCF() {
  local appName="${1}"
  local env="${2}"
  local org="${3}"
  local space="${4}"
  local api="${5}"
  local domain="${6}"
  local instances="${7}"

  downloadJar 'true' ${REPO_WITH_JARS} ${PROJECT_GROUP_ID} ${PROJECT_ARTIFACT_ID} ${PIPELINE_VERSION}

  # deploy app
  jarName="${PROJECT_ARTIFACT_ID}-${PIPELINE_VERSION}"

  # Create service Each app has to customize that scripts
  createServices

  deployAppWithName "${appName}" "${jarName}" "${env}" "${domain}" "${instances}"
  JAVA_OPTS=$(createJaveOPTs ${appName} ${env})

  cf set-env ${appName} JAVA_OPTS "${JAVA_OPTS}"
  restartApp "${appName}"
}

function buildGradleProperties() {
  echo nexusPublicRepoURL=${nexusPublicRepoURL} > gradle.properties
  echo nexusReleaseRepoURL=${nexusReleaseRepoURL} >> gradle.properties
  echo nexusSnapshotRepoURL=${nexusSnapshotRepoURL} >> gradle.properties
  echo nexusUsername=${nexusUsername} >> gradle.properties
  echo nexusPassword=${nexusPassword} >> gradle.properties
  echo buildNumber=${BUILD_NUMBER} >> gradle.properties
  echo classifier=${BUILD_NUMBER} >> gradle.properties
  echo cfUsername=changeme >> gradle.properties
  echo cfPassword=changeme >> gradle.properties
  echo ARTIFACT_TYPE=${ARTIFACT_TYPE} >> gradle.properties
}

function appExist() {
  local appName="${1}"
  cf app $appName
}

export PROJECT_TYPE=$( projectType )
export OUTPUT_FOLDER=$( outputFolder )
export TEST_REPORTS_FOLDER=$( testResultsFolder )

echo "Project type [${PROJECT_TYPE}]"
echo "Output folder [${OUTPUT_FOLDER}]"
echo "Test reports folder [${TEST_REPORTS_FOLDER}]"
