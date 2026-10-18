variable "gcp_project_id" {
  type        = string
  description = "The GCP Project ID where resources will be deployed."
}

variable "gcp_region" {
  type        = string
  description = "GCP Region for Cloud Run and Cloud SQL."
  default     = "us-east1"
}

variable "db_username" {
  type        = string
  description = "Username for the Cloud SQL PostgreSQL instance."
  default     = "postgres"
}

variable "db_password" {
  type        = string
  description = "Password for the Cloud SQL PostgreSQL user."
  sensitive   = true
}

variable "neo4j_password" {
  type        = string
  description = "Password for the Neo4j Enterprise database instance."
  sensitive   = true
}

