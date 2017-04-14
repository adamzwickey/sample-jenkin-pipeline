#!/bin/bash

set -ex

buildGradleProperties
./gradlew integrationTest
