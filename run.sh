#!/bin/bash

chmod 755 /home/application/data/apps/saasno-backend/logback.xml

# Menjalankan aplikasi dengan mengutamakan JAVA_OPTS dari docker-compose
java $JAVA_OPTS -Dspring.profiles.active=prod -jar /home/application/data/apps/saasno-backend/saasno-backend.jar --logging.config=/home/application/data/apps/saasno-backend/logback.xml