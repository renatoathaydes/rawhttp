#!/bin/bash

# command that tests that the server is accepting connections
CMD="curl localhost:8082 >& /dev/null"

# start the server
../rawhttp serve . -p 8082 &
SERVER_PID=$!


STEP=0.001      # sleep between tries, in seconds
MAX_TRIES=500
TRIES_LEFT=${MAX_TRIES}
eval ${CMD}
while [[ $? -ne 0 ]]; do
  ((TRIES_LEFT--))
  echo -ne "Tries left: $TRIES_LEFT"\\r
  if [[ TRIES_LEFT -eq 0 ]]; then
    echo "Server not started within timeout"
    exit 1
  fi
  sleep ${STEP}
  eval ${CMD}
done
if [[ ${TRIES_LEFT} -gt 0 ]]; then
  TIME=$(echo "$STEP * ($MAX_TRIES - $TRIES_LEFT)" | bc)
  echo "Server connected in $TIME seconds"
  #./rawhttp send -t "GET localhost:8082/hello"
fi

kill ${SERVER_PID}