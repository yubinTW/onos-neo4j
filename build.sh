#!/bin/bash

# 1. compile onos app 
# 2. clear old app
# 3. install and activate new app

mvn clean install

if [[ $? -eq 0 ]]
then

    ./clear-old-app.exp
    onos-app localhost install target/yubin-app-1.0-SNAPSHOT.oar
    ./activate-new-app.exp

    echo "build successfully"

else
    echo "build fail"
fi

echo ""
