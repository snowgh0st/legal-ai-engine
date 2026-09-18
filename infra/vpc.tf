# Private VPC Network
resource "google_compute_network" "vpc_network" {
  name                    = "lexintel-vpc"
  auto_create_subnetworks = true
}

# Reserved IP Range for Private Services (Cloud SQL Access)
resource "google_compute_global_address" "private_ip_address" {
  name          = "lexintel-private-ip"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.vpc_network.id
}

# Private Service Connection
resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.vpc_network.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_address.name]
}

# Serverless VPC Access Connector (For Cloud Run to talk to internal DBs)
resource "google_vpc_access_connector" "connector" {
  name          = "lexintel-vpc-connector"
  region        = var.gcp_region
  ip_cidr_range = "10.8.0.0/28"
  network       = google_compute_network.vpc_network.name
}