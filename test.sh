#!/usr/bin/env bash

# shellcheck disable=SC2046
env $(grep -v '^#' .env | xargs) mvn -Dtest=DigigesWebsiteTest clean test
