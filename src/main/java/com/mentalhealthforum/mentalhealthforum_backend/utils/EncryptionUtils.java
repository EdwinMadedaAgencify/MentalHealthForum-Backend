package com.mentalhealthforum.mentalhealthforum_backend.utils;

import com.mentalhealthforum.mentalhealthforum_backend.config.EncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class EncryptionUtils {

    private final EncryptionProperties encryptionProperties;
    private static final String ALGORITHM = "AES";

    public EncryptionUtils(EncryptionProperties encryptionProperties) {
        this.encryptionProperties = encryptionProperties;
    }

    public String encrypt(String data){
        try{
            SecretKeySpec spec = new SecretKeySpec(encryptionProperties.getKey().getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, spec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        }  catch (Exception e) {
            throw new RuntimeException("Encryption failed",e);
        }
    }

    public String decrypt(String encryptedData){
      try{
          SecretKeySpec spec = new SecretKeySpec(encryptionProperties.getKey().getBytes(), ALGORITHM);
          Cipher cipher = Cipher.getInstance(ALGORITHM);
          cipher.init(Cipher.DECRYPT_MODE, spec);
          return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
      } catch (Exception e) {
          throw new RuntimeException("Decryption failed", e);
      }
    }

}
