package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.Map;

/** What the player holds right now: item name to quantity, per container. Immutable. */
public final class ItemHoldings {

   public static final ItemHoldings NONE = new ItemHoldings(Collections.emptyMap(), Collections.emptyMap(),
      Collections.emptyMap(), false, false);

   private final Map<String, Integer> inventory;
   private final Map<String, Integer> equipment;
   private final Map<String, Integer> bank;
   private final boolean bankKnown;
   private final boolean loggedIn;

   public ItemHoldings(final Map<String, Integer> inventory, final Map<String, Integer> equipment,
      final Map<String, Integer> bank, final boolean bankKnown, final boolean loggedIn) {
      this.inventory = Collections.unmodifiableMap(inventory);
      this.equipment = Collections.unmodifiableMap(equipment);
      this.bank = Collections.unmodifiableMap(bank);
      this.bankKnown = bankKnown;
      this.loggedIn = loggedIn;
   }

   public Map<String, Integer> getInventory() {
      return inventory;
   }

   public Map<String, Integer> getEquipment() {
      return equipment;
   }

   public Map<String, Integer> getBank() {
      return bank;
   }

   /** False until the bank has been opened this session. */
   public boolean isBankKnown() {
      return bankKnown;
   }

   public boolean isLoggedIn() {
      return loggedIn;
   }
}
