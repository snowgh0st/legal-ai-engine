resource "google_compute_instance" "neo4j_vm" {
  name         = "lexintel-neo4j-vm"
  machine_type = "e2-small"
  zone         = "${var.gcp_region}-b"

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 20 # GB
    }
  }

  network_interface {
    network = google_compute_network.vpc_network.name
    access_config {
      # Grants external IP for temporary local dev access
    }
  }

  # Dynamically render the script using templatefile
  metadata_startup_script = templatefile("${path.module}/scripts/startup-neo4j.sh", {
    neo4j_password = var.neo4j_password
  })

  tags = ["neo4j-node"]
}

