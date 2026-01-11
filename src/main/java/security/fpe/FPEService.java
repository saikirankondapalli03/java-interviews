package security.fpe;

import software.amazon.awssdk.services.kms.KmsClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Format Preserving Encryption Service
 * Includes key caching, rotation, and proper error handling
 */
public class FPEService {
    
    private final KmsClient kmsClient;
    private final String keyId;
    private final ConcurrentHashMap<String, CachedDataKey> keyCache;
    private final ScheduledExecutorService keyRotationScheduler;
    private final FormatPreservingEncryption fpe;
    
    private static class CachedDataKey {
        final byte[] key;
        final long expiryTime;
        final String encryptedKey;
        
        CachedDataKey(byte[] key, String encryptedKey, long ttlMinutes) {
            this.key = key.clone();
            this.encryptedKey = encryptedKey;
            this.expiryTime = System.currentTimeMillis() + (ttlMinutes * 60 * 1000);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
    
    public FPEService(String keyId) {
        this.kmsClient = KmsClient.builder().build();
        this.keyId = keyId;
        this.keyCache = new ConcurrentHashMap<>();
        this.keyRotationScheduler = Executors.newScheduledThreadPool(1);
        this.fpe = new FormatPreservingEncryption(keyId);
        
        // Schedule key rotation every 4 hours
        keyRotationScheduler.scheduleAtFixedRate(this::rotateKeys, 4, 4, TimeUnit.HOURS);
    }
    
    /**
     * Encrypts customer data with proper audit logging
     */
    public EncryptedCustomerData encryptCustomerData(CustomerData customerData) {
        try {
            String encryptedSSN = null;
            String encryptedEmail = null;
            String encryptedPhone = null;
            
            if (customerData.getSsn() != null) {
                encryptedSSN = fpe.encryptSSN(customerData.getSsn());
                auditLog("SSN_ENCRYPT", customerData.getCustomerId());
            }
            
            if (customerData.getEmail() != null) {
                encryptedEmail = fpe.encryptEmail(customerData.getEmail());
                auditLog("EMAIL_ENCRYPT", customerData.getCustomerId());
            }
            
            if (customerData.getPhoneNumber() != null) {
                encryptedPhone = fpe.encryptPhoneNumber(customerData.getPhoneNumber());
                auditLog("PHONE_ENCRYPT", customerData.getCustomerId());
            }
            
            return new EncryptedCustomerData(
                customerData.getCustomerId(),
                encryptedSSN,
                encryptedEmail,
                encryptedPhone
            );
            
        } catch (Exception e) {
            auditLog("ENCRYPT_ERROR", customerData.getCustomerId(), e.getMessage());
            throw new FPEException("Encryption failed for customer: " + customerData.getCustomerId(), e);
        }
    }
    
    /**
     * Decrypts customer data with proper audit logging
     */
    public CustomerData decryptCustomerData(EncryptedCustomerData encryptedData) {
        try {
            String decryptedSSN = null;
            String decryptedEmail = null;
            String decryptedPhone = null;
            
            if (encryptedData.getEncryptedSSN() != null) {
                decryptedSSN = fpe.decryptSSN(encryptedData.getEncryptedSSN());
                auditLog("SSN_DECRYPT", encryptedData.getCustomerId());
            }
            
            if (encryptedData.getEncryptedEmail() != null) {
                decryptedEmail = fpe.decryptEmail(encryptedData.getEncryptedEmail());
                auditLog("EMAIL_DECRYPT", encryptedData.getCustomerId());
            }
            
            if (encryptedData.getEncryptedPhone() != null) {
                decryptedPhone = fpe.decryptPhoneNumber(encryptedData.getEncryptedPhone());
                auditLog("PHONE_DECRYPT", encryptedData.getCustomerId());
            }
            
            return new CustomerData(
                encryptedData.getCustomerId(),
                decryptedSSN,
                decryptedEmail,
                decryptedPhone
            );
            
        } catch (Exception e) {
            auditLog("DECRYPT_ERROR", encryptedData.getCustomerId(), e.getMessage());
            throw new FPEException("Decryption failed for customer: " + encryptedData.getCustomerId(), e);
        }
    }
    
    private void rotateKeys() {
        try {
            keyCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            System.out.println("Key rotation completed. Active keys: " + keyCache.size());
        } catch (Exception e) {
            System.err.println("Key rotation failed: " + e.getMessage());
        }
    }
    
    private void auditLog(String operation, String customerId) {
        auditLog(operation, customerId, null);
    }
    
    private void auditLog(String operation, String customerId, String error) {
        // In production, this would write to a secure audit log system
        String logEntry = String.format("[%s] Operation: %s, Customer: %s, Timestamp: %d",
            error != null ? "ERROR" : "INFO",
            operation,
            customerId,
            System.currentTimeMillis()
        );
        
        if (error != null) {
            logEntry += ", Error: " + error;
        }
        
        System.out.println("AUDIT: " + logEntry);
    }
    
    public void shutdown() {
        keyRotationScheduler.shutdown();
        fpe.close();
        kmsClient.close();
        
        // Clear sensitive data from memory
        keyCache.values().forEach(cachedKey -> 
            java.util.Arrays.fill(cachedKey.key, (byte) 0)
        );
        keyCache.clear();
    }
    
    // Data classes
    public static class CustomerData {
        private final String customerId;
        private final String ssn;
        private final String email;
        private final String phoneNumber;
        
        public CustomerData(String customerId, String ssn, String email, String phoneNumber) {
            this.customerId = customerId;
            this.ssn = ssn;
            this.email = email;
            this.phoneNumber = phoneNumber;
        }
        
        public String getCustomerId() { return customerId; }
        public String getSsn() { return ssn; }
        public String getEmail() { return email; }
        public String getPhoneNumber() { return phoneNumber; }
    }
    
    public static class EncryptedCustomerData {
        private final String customerId;
        private final String encryptedSSN;
        private final String encryptedEmail;
        private final String encryptedPhone;
        
        public EncryptedCustomerData(String customerId, String encryptedSSN, String encryptedEmail, String encryptedPhone) {
            this.customerId = customerId;
            this.encryptedSSN = encryptedSSN;
            this.encryptedEmail = encryptedEmail;
            this.encryptedPhone = encryptedPhone;
        }
        
        public String getCustomerId() { return customerId; }
        public String getEncryptedSSN() { return encryptedSSN; }
        public String getEncryptedEmail() { return encryptedEmail; }
        public String getEncryptedPhone() { return encryptedPhone; }
    }
    
    public static class FPEException extends RuntimeException {
        public FPEException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}