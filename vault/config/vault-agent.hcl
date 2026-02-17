vault {
  address = "http://vault:8200"

  # Retry on transient failures (e.g. vault restarting)
  retry {
    num_retries = 5
  }
}

# AppRole auth - role-id and secret-id are written by the entrypoint command
auto_auth {
  method "approle" {
    config = {
      role_id_file_path                   = "/vault/role-id"
      secret_id_file_path                 = "/vault/secret-id"
      remove_secret_id_file_after_reading = false  # set true in prod for one-shot secret IDs
    }
  }

  # Cache the token on disk so agent can recover after restart
  sink "file" {
    config = {
      path = "/vault/agent-token"
      mode = 0640
    }
  }
}

# In-process cache: apps talk to the agent instead of vault directly
cache {
  use_auto_auth_token = true
}

# Agent acts as a local Vault proxy on this port
listener "tcp" {
  address     = "0.0.0.0:8007"
  tls_disable = true  # internal network only; agent<->vault is the sensitive hop
}