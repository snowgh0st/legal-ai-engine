terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
  # Note: Store state in a GCS bucket to allow GitHub Actions to track state
  backend "gcs" {
    bucket = "lexintel-tf-state-bucket"
    prefix = "terraform/state"
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}