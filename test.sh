#!/usr/bin/env bash

# shellcheck disable=SC2046
env $(grep -v '^#' .env | xargs) mvn -Dtest=DigigesWebsiteTest clean test

find ./screenshots -type f -not -name "*stitched.png" -exec rm -f {} \;
