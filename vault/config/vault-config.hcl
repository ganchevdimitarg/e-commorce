# Storage backend - filesystem for single-node prod setup
storage "file" {
  path = "/vault/data"
}

# TCP listener
listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_cert_file = "/vault/tls/vault.crt"
  tls_key_file  = "/vault/tls/vault.key"

  # If you're terminating TLS at a load balancer/reverse proxy, use this instead:
  # tls_disable = true
}

# Must match the externally accessible address (used in redirect URLs)
api_addr     = "http://vault:8200"
cluster_addr = "https://vault:8201"

ui = true

# How long before a token is automatically renewed
# default_lease_ttl = "168h"
# max_lease_ttl     = "720h"