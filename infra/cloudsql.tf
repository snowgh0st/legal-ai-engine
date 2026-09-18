resource "google_sql_database_instance" "postgres" {
  name             = "lexintel-postgres-instance"
  database_version = "POSTGRES_15"
  region           = var.gcp_region

  depends_on = [google_service_networking_connection.private_vpc_connection]

  settings {
    tier = "db-f1-micro" # Cost-effective tier (~$10/mo)

    ip_configuration {
      ipv4_enabled    = true # Enabled temporarily for local dev testing
      private_network = google_compute_network.vpc_network.id

      # Authorize your local public IP temporarily for local testing
      authorized_networks {
        value = var.local_ip_address
        name  = "local-dev"
      }
    }
  }
}

resource "google_sql_database" "database" {
  name     = "audit_db"
  instance = google_sql_database_instance.postgres.name
}

resource "google_sql_user" "users" {
  name     = var.db_username
  instance = google_sql_database_instance.postgres.name
  password = var.db_password
}