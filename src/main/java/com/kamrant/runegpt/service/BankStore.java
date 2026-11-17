package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kamrant.runegpt.model.SavedBank;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Keeps the last-seen bank on this computer, so RuneGPT knows it from the start of the next session instead of
 * only after the bank is opened. One file per account in {@code ~/.runelite/runegpt/}, named by RuneLite's numeric
 * account hash (no username), never synced. Only item ids and quantities are stored.
 */
@Slf4j
@Singleton
public class BankStore {

   private static final String PREFIX = "bank-";
   private static final String SUFFIX = ".json";

   private final File dir;
   private final Gson gson;

   @Inject
   public BankStore(final Gson gson) {
      this(new File(RuneLite.RUNELITE_DIR, "runegpt"), gson);
   }

   public BankStore(final File dir, final Gson gson) {
      this.dir = dir;
      this.gson = gson;
   }

   /** The saved bank for {@code accountHash}, or null if there is none (or it can't be read). */
   public SavedBank load(final long accountHash) {
      if (accountHash == -1) {
         return null;
      }
      final File file = file(accountHash);
      if (!file.isFile()) {
         return null;
      }
      try (Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
         final SavedBank saved = gson.fromJson(in, SavedBank.class);
         return saved == null || saved.getItems().isEmpty() ? null : saved;
      } catch (final IOException | JsonSyntaxException e) {
         log.warn("RuneGPT: couldn't read the saved bank: {}", e.getMessage());
         return null;
      }
   }

   /** Saves the bank for {@code accountHash}, replacing the previous file atomically. */
   public void save(final long accountHash, final Map<Integer, Integer> items, final long savedAt) {
      if (accountHash == -1 || items.isEmpty()) {
         return;
      }
      try {
         Files.createDirectories(dir.toPath());
         final File tmp = new File(dir, PREFIX + accountHash + SUFFIX + ".tmp");
         try (Writer out = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(new SavedBank(savedAt, items), out);
         }
         Files.move(tmp.toPath(), file(accountHash).toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (final IOException e) {
         log.warn("RuneGPT: couldn't save the bank", e);
      }
   }

   /** Deletes every saved bank (the player turned the setting off). */
   public void deleteAll() {
      final File[] files = dir.listFiles((d, name) -> name.startsWith(PREFIX)
         && (name.endsWith(SUFFIX) || name.endsWith(SUFFIX + ".tmp")));
      if (files == null) {
         return;
      }
      for (final File f : files) {
         if (!f.delete()) {
            log.warn("RuneGPT: couldn't delete saved bank {}", f.getName());
         }
      }
   }

   private File file(final long accountHash) {
      return new File(dir, PREFIX + accountHash + SUFFIX);
   }
}
