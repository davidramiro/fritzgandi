package com.davidjusto.fritzgandi.util;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

/**
 * @author davidramiro
 */
public class ValidationUtils {

    private ValidationUtils() {}

    public static boolean isValidIp(final String ip) {
        return new InetAddressValidator().isValidInet4Address(ip);
    }

    public static boolean isValidDomain(final String domain) {
        return DomainValidator.getInstance().isValid(domain);
    }
}
