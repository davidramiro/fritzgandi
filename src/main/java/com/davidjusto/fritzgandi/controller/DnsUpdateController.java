package com.davidjusto.fritzgandi.controller;

import com.davidjusto.fritzgandi.util.ValidationUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * @author davidramiro
 */
@RestController
@RequestMapping("/api")
public class DnsUpdateController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsUpdateController.class);

    @Value("${gandi.api.url}")
    private String gandiBaseUrl;
    private RestTemplate restTemplate;

    @GetMapping("/status")
    public boolean status() {
        return true;
    }

    @RequestMapping(path = "/update")
    public ResponseEntity<String> update(@RequestParam String apikey, @RequestParam String domain, @RequestParam String subdomain,
                                         @RequestParam String ip) {

        String requestDescription = String.format("FQDN: %s.%s, IP: %s", subdomain, domain, ip);
        LOGGER.info("Received DNS update request for {}", requestDescription);

        if (!ValidationUtils.isValidIp(ip)) {
            LOGGER.error("IP validation failed for {}. Only IPv4 addresses are allowed.", ip);
            return new ResponseEntity<>("IP incorrectly formatted.", HttpStatus.BAD_REQUEST);
        } else if (!ValidationUtils.isValidDomain(domain)) {
            LOGGER.error("Domain validation failed for {}.", ip);
            return new ResponseEntity<>("IP incorrectly formatted.", HttpStatus.BAD_REQUEST);
        }

        this.restTemplate = new RestTemplateBuilder(rt -> rt.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Apikey " + apikey);
            return execution.execute(request, body);
        })).build();

        String updateRecordEndpoint = String.format("%s/domains/%s/records/%s/A", this.gandiBaseUrl, domain, subdomain);

        try {
            ResponseEntity<String> response =  updateDnsEntry(ip, updateRecordEndpoint);

            if (response.getStatusCode().equals(HttpStatus.CREATED)) {
                LOGGER.info("Gandi confirmed DNS entry update.");
                return new ResponseEntity<>(String.format("Record successfully created. %s", requestDescription),
                        HttpStatus.OK);
            } else {
                LOGGER.error("Gandi returned error: {}, {}", response.getStatusCode(), response.getBody());
                return new ResponseEntity<>(response.getBody(), response.getStatusCode());
            }
        } catch (HttpClientErrorException.Unauthorized ex) {
            LOGGER.error("Gandi returned 401.");
            return new ResponseEntity<>("Unauthorized. Wrong API key?", HttpStatus.UNAUTHORIZED);
        } catch (HttpClientErrorException.NotFound ex) {
            LOGGER.error("Gandi returned 404.");
            return new ResponseEntity<>("Not found. Wrong domain?", HttpStatus.NOT_FOUND);
        }

    }

    private ResponseEntity<String> updateDnsEntry(String ip, String endpoint) {
        JSONObject putRequest = new JSONObject();
        putRequest.put("rrset_ttl", 300);
        JSONArray jsonIp = new JSONArray();
        jsonIp.put(ip);
        putRequest.put("rrset_values", jsonIp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(putRequest.toString(), headers);

        return restTemplate.exchange(endpoint, HttpMethod.PUT, request, String.class);
    }
}
