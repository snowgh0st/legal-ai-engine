output "postgres_public_ip" {
  value       = google_sql_database_instance.postgres.public_ip_address
  description = "Public IP address of the Cloud SQL PostgreSQL instance for local Spring Boot testing."
}

output "postgres_private_ip" {
  value       = google_sql_database_instance.postgres.private_ip_address
  description = "Private internal VPC IP address of Cloud SQL (used by Cloud Run via VPC connector)."
}

output "postgres_db_name" {
  value       = google_sql_database.database.name
  description = "Name of the created PostgreSQL database."
}

output "neo4j_vm_public_ip" {
  value       = google_compute_instance.neo4j_vm.network_interface[0].access_config[0].nat_ip
  description = "Public IP address of the Compute Engine VM running Dockerized Neo4j."
}

output "neo4j_vm_private_ip" {
  value       = google_compute_instance.neo4j_vm.network_interface[0].network_ip
  description = "Private internal VPC IP address of the Neo4j VM."
}

output "neo4j_bolt_url" {
  value       = "bolt://${google_compute_instance.neo4j_vm.network_interface[0].access_config[0].nat_ip}:7687"
  description = "Bolt URI for local testing connection to GCP Neo4j."
}

output "vpc_connector_id" {
  value       = google_vpc_access_connector.connector.id
  description = "ID of the Serverless VPC Access Connector for Cloud Run integration."
}