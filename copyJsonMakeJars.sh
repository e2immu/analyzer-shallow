#!/bin/bash

rm -rf e2immu-shallow-aapi/src/main/resources/org/e2immu/analyzer/shallow/aapi/analyzedPackageFiles/*
mv e2immu-shallow-analyzer/build/json/* e2immu-shallow-aapi/src/main/resources/org/e2immu/analyzer/shallow/aapi/analyzedPackageFiles/
cd e2immu-shallow-aapi/src/main/resources/org/e2immu/analyzer/shallow/aapi/analyzedPackageFiles/jdk/openjdk-21.0.7
jar cf ../openjdk-21.0.7.jar *.json
cd ../openjdk-23.0.2/
 jar cf ../openjdk-23.0.2.jar *.json
 cd ../../libs/
jar cf ../libs.jar */*.json
