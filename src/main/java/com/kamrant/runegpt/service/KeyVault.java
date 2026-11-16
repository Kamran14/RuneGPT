package com.kamrant.runegpt.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Encrypts API keys before they're stored in RuneLite's settings, so synced profiles, copied profile files
 * and screenshots of the settings file never contain a usable key.
 *
 * <p>Values are AES-256-GCM encrypted ({@code enc:v1:<base64 iv+ciphertext>}), bound to the setting's name.
 * The encryption key is random, created on first use, and kept in {@code ~/.runelite/runegpt/secret.key} on
 * this computer only (outside the RuneLite profile, so it is never synced). Anything running as this
 * computer's user can still read that file; protecting against that would need the OS credential store.</p>
 */
@Slf4j
@Singleton
public class KeyVault {

   public static final String PREFIX = "enc:v1:";

   private static final String CIPHER = "AES/GCM/NoPadding";
   private static final int KEY_BYTES = 32;
   private static final int IV_BYTES = 12;
   private static final int TAG_BITS = 128;

   private final File keyFile;
   private final SecureRandom random = new SecureRandom();
   private byte[] key;

   @Inject
   public KeyVault() {
      this(new File(new File(RuneLite.RUNELITE_DIR, "runegpt"), "secret.key"));
   }

   /** For tests: use a specific key file. */
   public KeyVault(final File keyFile) {
      this.keyFile = keyFile;
   }

   public static boolean isEncrypted(final String stored) {
      return stored != null && stored.startsWith(PREFIX);
   }

   /** Encrypts {@code plain} for the setting {@code name}. Returns null if encryption isn't possible. */
   public String encrypt(final String plain, final String name) {
      try {
         final byte[] iv = new byte[IV_BYTES];
         random.nextBytes(iv);
         final Cipher cipher = Cipher.getInstance(CIPHER);
         cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
         cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
         final byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
         final byte[] out = new byte[iv.length + ct.length];
         System.arraycopy(iv, 0, out, 0, iv.length);
         System.arraycopy(ct, 0, out, iv.length, ct.length);
         return PREFIX + Base64.getEncoder().encodeToString(out);
      } catch (final GeneralSecurityException | IOException e) {
         log.warn("RuneGPT: couldn't encrypt {}", name, e);
         return null;
      }
   }

   /**
    * The usable key for a stored setting value: decrypted if it's encrypted, unchanged if it's still plain
    * (not migrated yet), empty if unset. Returns null if it's encrypted but can't be decrypted here, e.g. it
    * was synced from another computer or the key file was deleted.
    */
   public String reveal(final String stored, final String name) {
      if (stored == null || stored.trim().isEmpty()) {
         return "";
      }
      if (!isEncrypted(stored)) {
         return stored.trim();
      }
      try {
         final byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()).trim());
         if (in.length <= IV_BYTES) {
            return null;
         }
         final Cipher cipher = Cipher.getInstance(CIPHER);
         cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
         cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
         return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
      } catch (final GeneralSecurityException | IllegalArgumentException | IOException e) {
         log.debug("RuneGPT: couldn't decrypt {} on this computer", name);
         return null;
      }
   }

   /** Loads the local key, creating it (owner-readable only, where supported) on first use. */
   private synchronized byte[] key() throws IOException {
      if (key != null) {
         return key;
      }
      if (keyFile.isFile()) {
         final byte[] existing = Base64.getDecoder().decode(new String(Files.readAllBytes(keyFile.toPath()),
            StandardCharsets.US_ASCII).trim());
         if (existing.length == KEY_BYTES) {
            key = existing;
            return key;
         }
         log.warn("RuneGPT: key file is invalid, creating a new one (saved API keys will need re-entering)");
      }
      final byte[] fresh = new byte[KEY_BYTES];
      random.nextBytes(fresh);
      final File dir = keyFile.getParentFile();
      if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
         throw new IOException("can't create " + dir);
      }
      Files.write(keyFile.toPath(), Base64.getEncoder().encode(fresh),
         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      restrictToOwner(keyFile);
      key = fresh;
      return key;
   }

   private static void restrictToOwner(final File file) {
      try {
         Files.setPosixFilePermissions(file.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      } catch (final UnsupportedOperationException | IOException e) {
         // Windows: best effort. Files in the user profile are already private to the user by default.
         file.setReadable(false, false);
         file.setReadable(true, true);
         file.setWritable(false, false);
         file.setWritable(true, true);
      }
   }
}
