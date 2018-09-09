#!/usr/bin/env bash

ROOT=$(cd $(dirname $0)/..; pwd)
EXAMPLE_ROOT=$ROOT/example
LOCAL_MAVEN_REPO=$ROOT/build/repo
WRITABLE_BUCKET="$1"

if [ -z "$WRITABLE_BUCKET" ]; then
    echo "[USAGE] $(basename $0) WRITABLE_BUCKET"
    exit 1
fi


(
  cd $EXAMPLE_ROOT

  ## to remove cache
  rm -rfv .digdag

  ## run
  digdag run example.dig -c allow.properties -p repos=${LOCAL_MAVEN_REPO} -p WRITABLE_BUCKET=${WRITABLE_BUCKET} --no-save
)
