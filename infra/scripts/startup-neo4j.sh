#!/bin/bash
# Install Docker
apt-get update
apt-get install -y docker.io docker-compose

# Run Dockerized Neo4j Enterprise with APOC
docker run -d \
  --name neo4j-enterprise \
  --restart always \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/${neo4j_password} \
  -e NEO4J_ACCEPT_LICENSE_AGREEMENT=yes \
  -e NEO4J_PLUGINS='["apoc"]' \
  -e NEO4J_dbms_security_procedures_unrestricted=apoc.\* \
  neo4j:5.18.0-enterprise