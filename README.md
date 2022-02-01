# FritzGandi DynDNS Update Microservice

Spring Boot microservice to allow FritzBox routers to update Gandi DNS entries when obtaining a new IP address.
Uses the new LiveDNS API. Tested with Java 8, 11 and 17.

## Requirements
- Gandi API Key from [Gandi Account settings](https://account.gandi.net/) under Security
- FritzBox router with up-to-date firmware

## Usage

- Download the latest release jar

OR
- Clone the repo, issue `mvn package` and take the jar file from `target` directory

  
- Run the application with `java -jar fritzgandi-<VERSION>.jar`
- Log into your FritzBox
- Navigate to `Permit Access` -> `DynDNS`
- Enable DynDNS and use `User-defined` as Provider
- Enter the following URL: `http://{HOST}:{PORT}/api/update?apikey=<passwd>&domain=<domain>&subdomain=<username>&ip=<ipaddr>`
  - Replace the `{HOST}` and `{PORT}` with your deployment of the application
  - By default, the application uses port `9595`
- Enter your base domain in the `Domain Name` field
- Enter the subdomain to be updated in the `Username` field
- Enter your Gandi API-Key in the `Password` field

![](https://kore.cc/fritzgandi/settings.png "FritzBox DynDNS Settings")


Right after you save the settings, your FritzBox will make a request to the application. You should see the following
success message in its log:

![](https://kore.cc/fritzgandi/success.png "Success Message")

Your FritzBox will now automatically communicate new IPs to the application. 

## Security notice
If you deploy this application outside your local network, I'd recommend you to use HTTPS for the requests.
Check below for an example on how to reverse proxy to this application with NGINX. 

## Linux systemd Service

To create a systemd service and run the application on boot, create a service file, for example under
`/etc/systemd/system/fritzgandi.service`.

Service file contents: 
```
[Unit]
Description=FritzGandi LiveDNS Microservice

[Service]
WorkingDirectory=/your/path
ExecStart=/bin/java -jar fritzgandi-VERSION.jar
User=youruser
Type=simple
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Don't run this as root. Make sure your `User` has access rights to the `WorkingDirectory` where the jar file stays.

Reload daemon, start the service, check its status:

```
sudo systemctl daemon-reload
sudo systemctl start fritzgandi.service
sudo systemctl status fritzgandi
```

If all is well, enable the service to be started on boot:

`sudo systemctl enable fritzgandi`

## NGINX Reverse Proxy

If you want to host the service and make sure it uses HTTPS, you can use a reverse proxy.
Shown below is an example of an NGINX + LetsEncrypt reverse proxy config for this microservice.

```
server {
    listen                  443 ssl http2;
    listen                  [::]:443 ssl http2;
    server_name             fritzgandi.yourdomain.com;

    # SSL
    ssl_certificate         /etc/letsencrypt/live/fritzgandi.yourdomain.com/fullchain.pem;
    ssl_certificate_key     /etc/letsencrypt/live/fritzgandi.yourdomain.com/privkey.pem;
    ssl_trusted_certificate /etc/letsencrypt/live/fritzgandi.yourdomain.com/chain.pem;

    # security headers
    add_header X-Frame-Options           "DENY";
    add_header X-XSS-Protection          "1; mode=block" always;
    add_header X-Content-Type-Options    "nosniff" always;
    add_header Referrer-Policy           "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy   "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
    add_header Strict-Transport-Security 'max-age=31536000; includeSubDomains; preload';
    add_header X-Permitted-Cross-Domain-Policies master-only;
    
    
    # . files
    location ~ /\.(?!well-known) {
        deny all;
    }

    # logging
    access_log              /var/log/nginx/fritzgandi.yourdomain.com.access.log;
    error_log               /var/log/nginx/fritzgandi.yourdomain.com.error.log warn;

    # reverse proxy
    location / {
        proxy_pass http://127.0.0.1:9595;
        proxy_http_version                 1.1;
        proxy_cache_bypass                 $http_upgrade;
        
        # Proxy headers
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        "upgrade";
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-Port  $server_port;
        
        # Proxy timeouts
        proxy_connect_timeout              60s;
        proxy_send_timeout                 60s;
        proxy_read_timeout                 60s;
    }
}

# HTTP redirect
server {
    listen      80;
    listen      [::]:80;
    server_name fritzgandi.yourdomain.com;
    
    # ACME-challenge
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/_letsencrypt;
    }

    location / {
        return 301 https://fritzgandi.yourdomain.com$request_uri;
    }
}
```