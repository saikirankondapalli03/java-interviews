package security.fpe;

import java.util.regex.Pattern;

/**
 * Mock Format Preserving Encryption for demonstration purposes
 * This implementation doesn't use real AWS KMS but demonstrates the concept
 */
public class MockFormatPreservingEncryption {
    
    private final Pattern ssnPattern = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
    private final Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private final Pattern phonePattern = Pattern.compile("\\(\\d{3}\\) \\d{3}-\\d{4}");
    
    // Mock encryption key for demonstration
    private final int mockKey = 42;
    
    /**
     * Encrypts SSN while preserving XXX-XX-XXXX format
     */
    public String encryptSSN(String ssn) {
        if (!ssnPattern.matcher(ssn).matches()) {
            throw new IllegalArgumentException("Invalid SSN format");
        }
        
        String digits = ssn.replaceAll("-", "");
        String encryptedDigits = encryptNumericString(digits);
        return formatSSN(encryptedDigits);
    }
    
    /**
     * Encrypts email while preserving format structure
     */
    public String encryptEmail(String email) {
        if (!emailPattern.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        String[] parts = email.split("@");
        String localPart = encryptAlphanumericString(parts[0]);
        String domainPart = encryptAlphanumericString(parts[1]);
        return localPart + "@" + domainPart;
    }
    
    /**
     * Encrypts phone number while preserving (XXX) XXX-XXXX format
     */
    public String encryptPhoneNumber(String phone) {
        if (!phonePattern.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid phone format");
        }
        
        String digits = phone.replaceAll("[^\\d]", "");
        String encryptedDigits = encryptNumericString(digits);
        return formatPhoneNumber(encryptedDigits);
    }
    
    /**
     * Decrypts SSN
     */
    public String decryptSSN(String encryptedSSN) {
        String digits = encryptedSSN.replaceAll("-", "");
        String decryptedDigits = decryptNumericString(digits);
        return formatSSN(decryptedDigits);
    }
    
    /**
     * Decrypts email
     */
    public String decryptEmail(String encryptedEmail) {
        String[] parts = encryptedEmail.split("@");
        String localPart = decryptAlphanumericString(parts[0]);
        String domainPart = decryptAlphanumericString(parts[1]);
        return localPart + "@" + domainPart;
    }
    
    /**
     * Decrypts phone number
     */
    public String decryptPhoneNumber(String encryptedPhone) {
        String digits = encryptedPhone.replaceAll("[^\\d]", "");
        String decryptedDigits = decryptNumericString(digits);
        return formatPhoneNumber(decryptedDigits);
    }
    
    /**
     * Mock encryption for numeric strings
     */
    private String encryptNumericString(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            int digit = Character.getNumericValue(input.charAt(i));
            int encryptedDigit = (digit + mockKey + i) % 10;
            result.append(encryptedDigit);
        }
        return result.toString();
    }
    
    /**
     * Mock encryption for alphanumeric strings
     */
    private String encryptAlphanumericString(String input) {
        StringBuilder result = new StringBuilder();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-";
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int index = alphabet.indexOf(c);
            if (index != -1) {
                int newIndex = (index + mockKey + i) % alphabet.length();
                result.append(alphabet.charAt(newIndex));
            } else {
                result.append(c); // Keep non-alphabet characters as is
            }
        }
        return result.toString();
    }
    
    /**
     * Mock decryption for numeric strings
     */
    private String decryptNumericString(String encrypted) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < encrypted.length(); i++) {
            int digit = Character.getNumericValue(encrypted.charAt(i));
            int decryptedDigit = (digit - mockKey - i + 100) % 10; // +100 to handle negative numbers
            result.append(decryptedDigit);
        }
        return result.toString();
    }
    
    /**
     * Mock decryption for alphanumeric strings
     */
    private String decryptAlphanumericString(String encrypted) {
        StringBuilder result = new StringBuilder();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-";
        
        for (int i = 0; i < encrypted.length(); i++) {
            char c = encrypted.charAt(i);
            int index = alphabet.indexOf(c);
            if (index != -1) {
                int originalIndex = (index - mockKey - i + alphabet.length() * 10) % alphabet.length();
                result.append(alphabet.charAt(originalIndex));
            } else {
                result.append(c); // Keep non-alphabet characters as is
            }
        }
        return result.toString();
    }
    
    private String formatSSN(String digits) {
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
    }
    
    private String formatPhoneNumber(String digits) {
        return "(" + digits.substring(0, 3) + ") " + digits.substring(3, 6) + "-" + digits.substring(6);
    }
}