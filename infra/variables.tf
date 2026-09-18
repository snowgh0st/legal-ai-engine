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

variable "local_ip_address" {
  type        = string
  description = "Your local machine public IP address in CIDR notation (e.g., 203.0.113.5/32) for temporary dev access."
  default     = "0.0.0.0/0" # Override this with your actual public IP during testing
}