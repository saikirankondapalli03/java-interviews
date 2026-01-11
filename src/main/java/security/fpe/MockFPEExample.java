package security.fpe;

/**
 * Mock example of Format Preserving Encryption without AWS dependencies
 */
public class MockFPEExample {
    
    public static void main(String[] args) {
        System.out.println("=== Mock Format Preserving Encryption Demo ===");
        System.out.println("(This demo uses mock encryption for demonstration purposes)");
        System.out.println();
        
        MockFormatPreservingEncryption fpe = new MockFormatPreservingEncryption();
        
        try {
            // SSN Example
            String originalSSN = "123-45-6789";
            String encryptedSSN = fpe.encryptSSN(originalSSN);
            String decryptedSSN = fpe.decryptSSN(encryptedSSN);
            
            System.out.println("SSN Encryption:");
            System.out.println("Original:  " + originalSSN);
            System.out.println("Encrypted: " + encryptedSSN);
            System.out.println("Decrypted: " + decryptedSSN);
            System.out.println("Format preserved: " + encryptedSSN.matches("\\d{3}-\\d{2}-\\d{4}"));
            System.out.println();
            
            // Email Example
            String originalEmail = "john.doe@example.com";
            String encryptedEmail = fpe.encryptEmail(originalEmail);
            String decryptedEmail = fpe.decryptEmail(encryptedEmail);
            
            System.out.println("Email Encryption:");
            System.out.println("Original:  " + originalEmail);
            System.out.println("Encrypted: " + encryptedEmail);
            System.out.println("Decrypted: " + decryptedEmail);
            System.out.println("Format preserved: " + encryptedEmail.contains("@"));
            System.out.println();
            
            // Phone Number Example
            String originalPhone = "(555) 123-4567";
            String encryptedPhone = fpe.encryptPhoneNumber(originalPhone);
            String decryptedPhone = fpe.decryptPhoneNumber(encryptedPhone);
            
            System.out.println("Phone Encryption:");
            System.out.println("Original:  " + originalPhone);
            System.out.println("Encrypted: " + encryptedPhone);
            System.out.println("Decrypted: " + decryptedPhone);
            System.out.println("Format preserved: " + encryptedPhone.matches("\\(\\d{3}\\) \\d{3}-\\d{4}"));
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}