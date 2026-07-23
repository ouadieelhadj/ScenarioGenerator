#!/bin/bash

echo "==========================================" > project-info.txt
echo "Scenario Generator - Project Inspection" >> project-info.txt
echo "==========================================" >> project-info.txt
echo "" >> project-info.txt

echo "===== JAVA =====" >> project-info.txt
java -version >> project-info.txt 2>&1
echo "" >> project-info.txt

echo "===== MAVEN =====" >> project-info.txt
mvn -version >> project-info.txt 2>&1
echo "" >> project-info.txt

echo "===== ROOT =====" >> project-info.txt
pwd >> project-info.txt
echo "" >> project-info.txt

echo "===== MODULES =====" >> project-info.txt
find . -name pom.xml >> project-info.txt
echo "" >> project-info.txt

echo "===== ARTIFACTS =====" >> project-info.txt
find . -name pom.xml -exec grep -H "<artifactId>" {} \; >> project-info.txt
echo "" >> project-info.txt

echo "===== DATABASE =====" >> project-info.txt
find . -type f \( -name "application*.yml" -o -name "application*.yaml" -o -name "application*.properties" \) \
-exec grep -H -E "url:|username:|password:|spring.datasource.url|spring.datasource.username|spring.datasource.password" {} \; >> project-info.txt
echo "" >> project-info.txt

echo "===== FLYWAY / LIQUIBASE =====" >> project-info.txt
find . -name pom.xml -exec grep -H -E "flyway|liquibase" {} \; >> project-info.txt
echo "" >> project-info.txt

echo "===== CERTIFICATES / KEYS =====" >> project-info.txt
find . \( -name "*.jks" -o -name "*.p12" -o -name "*.pem" -o -name "*.crt" -o -name "*.cer" -o -name "*.lmk" \) >> project-info.txt
echo "" >> project-info.txt

echo "===== SQL FILES =====" >> project-info.txt
find . \( -name "*.sql" -o -name "*.dump" \) >> project-info.txt
echo "" >> project-info.txt

echo "===== TARGETS =====" >> project-info.txt
find . -name target >> project-info.txt
echo "" >> project-info.txt

echo "===== EXECUTABLE JARS =====" >> project-info.txt
find . -name "*.jar" >> project-info.txt
echo "" >> project-info.txt

echo "Inspection terminée."
echo "Le fichier project-info.txt a été généré."