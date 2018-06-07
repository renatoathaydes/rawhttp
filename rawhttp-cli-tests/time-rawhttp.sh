#!/bin/bash

# command that tests that the server is accepting connections
CMD="curl localhost:8081 >& /dev/null"


START_TIME=$(gdate +%s%3N)

# start the server
../rawhttp serve . -p 8082 &
SERVER_PID=$!

STEP=0.001      # sleep between tries, in seconds
TRIES=500
eval ${CMD}
while [[ $? -ne 0 ]]; do
  ((TRIES--))
  echo -ne "Tries left: $TRIES"\\r
  if [[ TRIES -eq 0 ]]; then
    echo "Server not started within timeout"
    exit 1
  fi
  sleep ${STEP}
  eval ${CMD}
done

END_TIME=$(gdate +%s%3N)
TIME=$(($END_TIME - $START_TIME))
echo "Server connected in $TIME ms"
#./rawhttp send -t "GET localhost:8082/hello"

kill ${SERVER_PID}