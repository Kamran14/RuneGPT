package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.CombatStyle;
import com.kamrant.runegpt.model.GearResult;
import com.kamrant.runegpt.model.GearSetup;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.model.MonsterStats;
import com.kamrant.runegpt.model.OwnedGear;
import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.WikiItem;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.wiki.WikiClient;
import com.kamrant.runegpt.wiki.WikiLookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Picks the highest-DPS gear setup for a monster from the items the player actually owns (worn, inventory
 * and bank), using RuneLite's item stats and the monster's defences and attributes from the OSRS Wiki.
 * No LLM calls: every number is computed from real item and monster stats.
 */
@Singleton
public class GearOptimizer {

   private static final int TOP_CANDIDATES_PER_SLOT = 4;

   /** Display order of equipment slots in a setup. */
   public static final int[] SLOT_ORDER = {
      EquipmentInventorySlot.HEAD.getSlotIdx(),
      EquipmentInventorySlot.CAPE.getSlotIdx(),
      EquipmentInventorySlot.AMULET.getSlotIdx(),
      EquipmentInventorySlot.WEAPON.getSlotIdx(),
      EquipmentInventorySlot.BODY.getSlotIdx(),
      EquipmentInventorySlot.SHIELD.getSlotIdx(),
      EquipmentInventorySlot.LEGS.getSlotIdx(),
      EquipmentInventorySlot.GLOVES.getSlotIdx(),
      EquipmentInventorySlot.BOOTS.getSlotIdx(),
      EquipmentInventorySlot.RING.getSlotIdx(),
      EquipmentInventorySlot.AMMO.getSlotIdx()
   };

   private static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
   private static final int AMULET = EquipmentInventorySlot.AMULET.getSlotIdx();
   private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
   private static final int SHIELD = EquipmentInventorySlot.SHIELD.getSlotIdx();
   private static final int GLOVES = EquipmentInventorySlot.GLOVES.getSlotIdx();
   private static final int AMMO = EquipmentInventorySlot.AMMO.getSlotIdx();

   private static final int KIND_BOW = 0;
   private static final int KIND_CROSSBOW = 1;
   private static final int KIND_SELF_AMMO = 2;
   private static final int KIND_THROWN = 3;

   // Standard elemental spells: name, required Magic level, base max hit, element, rune names.
   private static final Object[][] SPELLS = {
      { "Wind Strike", 1, 2, "Air", new String[] { "Air rune", "Mind rune" } },
      { "Water Strike", 5, 4, "Water", new String[] { "Water rune", "Air rune", "Mind rune" } },
      { "Earth Strike", 9, 6, "Earth", new String[] { "Earth rune", "Air rune", "Mind rune" } },
      { "Fire Strike", 13, 8, "Fire", new String[] { "Fire rune", "Air rune", "Mind rune" } },
      { "Wind Bolt", 17, 9, "Air", new String[] { "Air rune", "Chaos rune" } },
      { "Water Bolt", 23, 10, "Water", new String[] { "Water rune", "Air rune", "Chaos rune" } },
      { "Earth Bolt", 29, 11, "Earth", new String[] { "Earth rune", "Air rune", "Chaos rune" } },
      { "Fire Bolt", 35, 12, "Fire", new String[] { "Fire rune", "Air rune", "Chaos rune" } },
      { "Wind Blast", 41, 13, "Air", new String[] { "Air rune", "Death rune" } },
      { "Water Blast", 47, 14, "Water", new String[] { "Water rune", "Air rune", "Death rune" } },
      { "Earth Blast", 53, 15, "Earth", new String[] { "Earth rune", "Air rune", "Death rune" } },
      { "Fire Blast", 59, 16, "Fire", new String[] { "Fire rune", "Air rune", "Death rune" } },
      { "Wind Wave", 62, 17, "Air", new String[] { "Air rune", "Blood rune" } },
      { "Water Wave", 65, 18, "Water", new String[] { "Water rune", "Air rune", "Blood rune" } },
      { "Earth Wave", 70, 19, "Earth", new String[] { "Earth rune", "Air rune", "Blood rune" } },
      { "Fire Wave", 75, 20, "Fire", new String[] { "Fire rune", "Air rune", "Blood rune" } },
      { "Wind Surge", 81, 21, "Air", new String[] { "Air rune", "Wrath rune" } },
      { "Water Surge", 85, 22, "Water", new String[] { "Water rune", "Air rune", "Wrath rune" } },
      { "Earth Surge", 90, 23, "Earth", new String[] { "Earth rune", "Air rune", "Wrath rune" } },
      { "Fire Surge", 95, 24, "Fire", new String[] { "Fire rune", "Air rune", "Wrath rune" } }
   };

   private final WikiLookup wiki;
   private final PriceService prices;

   @Inject
   public GearOptimizer(final WikiLookup wiki, final PriceService prices) {
      this.wiki = wiki;
      this.prices = prices;
   }

   /**
    * Computes the best owned gear setup per combat style against {@code monsterInput}. Blocking (wiki
    * lookups for the monster and the top candidate items).
    */
   public GearResult optimize(final String monsterInput, final Map<String, String> picks, final PlayerSnapshot player) {
      final String trimmed = monsterInput == null ? "" : monsterInput.trim();
      if (trimmed.isEmpty()) {
         return GearResult.error(trimmed, "Enter a monster or boss name first.");
      }
      if (!player.isLoggedIn()) {
         return GearResult.error(trimmed, "Log in first so I can read your levels, worn gear and bank.");
      }
      if (player.getOwnedGear().isEmpty()) {
         return GearResult.error(trimmed, player.isBankKnown()
            ? "No equipable gear found in your equipment, inventory or bank."
            : "You aren't wearing or carrying any gear, and your bank hasn't been opened yet this session. Open your bank once so I can check it.");
      }

      final MonsterStats monster = wiki.monster(trimmed);
      if (monster == null) {
         return GearResult.error(trimmed, "The OSRS Wiki has no monster page for “" + trimmed
            + "”. Check the spelling or use its exact in-game name.");
      }

      final CombatStyle preferred = picks == null ? null : CombatStyle.fromLabel(picks.get("Attack style"));
      final boolean onTask = picks != null && "On task".equalsIgnoreCase(picks.get("Slayer"));

      // Group owned gear by slot and filter out top candidates whose wiki level requirements aren't met.
      final Set<String> hidden = new LinkedHashSet<>();
      final Map<Integer, List<OwnedGear>> bySlot = filterWearable(player, hidden);

      final List<GearSetup> setups = new ArrayList<>();
      final GearSetup melee = bestMelee(monster, bySlot, player, onTask);
      if (melee != null && !melee.getItems().isEmpty()) {
         setups.add(melee);
      }
      final GearSetup ranged = bestRanged(monster, bySlot, player, onTask);
      if (ranged != null && !ranged.getItems().isEmpty()) {
         setups.add(ranged);
      }
      final GearSetup magic = bestMagic(monster, bySlot, player, onTask);
      if (magic != null && !magic.getItems().isEmpty()) {
         setups.add(magic);
      }

      if (setups.isEmpty()) {
         return GearResult.error(trimmed, "None of your owned gear could be matched to a combat setup.");
      }

      // Highest DPS first; if the player picked a style, put that style first so it's shown at the top.
      setups.sort((a, b) -> {
         if (preferred != null) {
            if (a.getStyle() == preferred && b.getStyle() != preferred) {
               return -1;
            }
            if (b.getStyle() == preferred && a.getStyle() != preferred) {
               return 1;
            }
         }
         return Double.compare(b.getDps(), a.getDps());
      });

      final WorldPoint point = wiki.coordinates(monster.getName());
      final WorldTarget target = point == null
         ? WorldTarget.named(monster.getName())
         : new WorldTarget(monster.getName(), point, TargetSource.WIKI);

      return GearResult.of(trimmed, monster, setups, preferred, onTask, player.isBankKnown(),
         new ArrayList<>(hidden), target, WikiClient.pageUrl(monster.getName()));
   }

   /** Turns one {@link GearSetup} into a trackable {@link GuidePlan} with live item statuses and prices. */
   public GuidePlan toGuidePlan(final GearResult result, final GearSetup setup, final ItemHoldings holdings) {
      final List<GuideItem> items = new ArrayList<>();
      for (final int slot : SLOT_ORDER) {
         final OwnedGear g = setup.getItems().get(slot);
         if (g != null) {
            items.add(new GuideItem(g.getName(), 1, GuideItemKind.EQUIP, slotName(slot), ItemStatus.UNKNOWN));
         }
      }
      for (final String supply : setup.getCarry()) {
         items.add(new GuideItem(supply, 1, GuideItemKind.CARRY, null, ItemStatus.UNKNOWN));
      }
      final List<String> steps = new ArrayList<>();
      steps.add("Equip your " + setup.getStyle().getLabel().toLowerCase(Locale.ROOT) + " setup ("
         + setup.getWeaponName() + ").");
      steps.add("Attack style: " + setup.getAttack() + " (max hit " + setup.getMaxHit() + ", "
         + Math.round(setup.getHitChance() * 100) + "% accuracy, "
         + String.format(Locale.ROOT, "%.1f", setup.getDps()) + " DPS).");
      if (result.getMonster().is("dragon")) {
         steps.add("Keep dragonfire protection active.");
      }

      final List<GuideItem> priced = prices.withPrices(ItemStatusResolver.resolve(items, holdings));
      return new GuidePlan(result.getMonster().getName() + " (" + setup.getStyle().getLabel() + ")",
         steps, priced, null, result.getTarget(), 0);
   }

   // ---------------------------------------------------------------------------------------------
   // Melee
   // ---------------------------------------------------------------------------------------------

   private GearSetup bestMelee(final MonsterStats monster, final Map<Integer, List<OwnedGear>> bySlot,
      final PlayerSnapshot player, final boolean onTask) {
      final Map<Integer, OwnedGear> base = new LinkedHashMap<>();
      for (final int slot : SLOT_ORDER) {
         if (slot == WEAPON || slot == SHIELD || slot == AMMO) {
            continue;
         }
         final OwnedGear best = pickBest(bySlot.get(slot), g -> meleeSlotScore(g, monster, onTask));
         if (best != null) {
            base.put(slot, best);
         }
      }

      final OwnedGear bestShield = pickShield(bySlot.get(SHIELD), monster, g ->
         g.getStats().getStr() * 4.0 + Math.max(g.getStats().getAstab(),
            Math.max(g.getStats().getAslash(), g.getStats().getAcrush())));

      final List<OwnedGear> weapons = new ArrayList<>(bySlot.getOrDefault(WEAPON, Collections.emptyList()));
      weapons.add(null); // also consider unarmed if no melee weapon is owned

      GearSetup best = null;
      for (final OwnedGear weapon : weapons) {
         if (weapon != null && isRangedOrMagicOnlyWeapon(weapon)) {
            continue;
         }
         final Map<Integer, OwnedGear> equipped = new LinkedHashMap<>(base);
         if (weapon != null) {
            equipped.put(WEAPON, weapon);
         }
         final boolean twoHanded = weapon != null && weapon.getStats().isTwoHanded();
         // Against dragons, an anti-dragon shield with a 1h weapon beats a 2h weapon without protection.
         if (!twoHanded && bestShield != null) {
            equipped.put(SHIELD, bestShield);
         } else if (twoHanded && monster.is("dragon") && bestShield != null && isDragonfireShield(bestShield.getName())) {
            continue;
         }

         final int astab = sumInt(equipped.values(), ItemEquipmentStats::getAstab);
         final int aslash = sumInt(equipped.values(), ItemEquipmentStats::getAslash);
         final int acrush = sumInt(equipped.values(), ItemEquipmentStats::getAcrush);
         final int str = sumInt(equipped.values(), ItemEquipmentStats::getStr);
         final int speed = weapon == null || weapon.getStats().getAspeed() <= 0 ? 4 : weapon.getStats().getAspeed();

         final String[][] types = {
            { "Stab", String.valueOf(astab), String.valueOf(monster.getDefStab()),
               String.valueOf(weapon == null ? 0 : weapon.getStats().getAstab()) },
            { "Slash", String.valueOf(aslash), String.valueOf(monster.getDefSlash()),
               String.valueOf(weapon == null ? 0 : weapon.getStats().getAslash()) },
            { "Crush", String.valueOf(acrush), String.valueOf(monster.getDefCrush()),
               String.valueOf(weapon == null ? 0 : weapon.getStats().getAcrush()) }
         };
         final int maxWeaponStyle = weapon == null ? 0
            : maxOf(weapon.getStats().getAstab(), weapon.getStats().getAslash(), weapon.getStats().getAcrush());
         for (final String[] t : types) {
            final int styleEquipBonus = Integer.parseInt(t[1]);
            final int weaponStyleBonus = Integer.parseInt(t[3]);
            // In OSRS, a melee style a weapon can't use has <= 0 bonus on the weapon itself.
            if (weapon != null && weaponStyleBonus <= 0 && weaponStyleBonus < maxWeaponStyle) {
               continue;
            }
            final int monsterDef = Integer.parseInt(t[2]);
            final double mult = meleeMultiplier(equipped, weapon, monster, onTask);

            final int effAtt = (int) Math.floor((player.level(Skill.ATTACK) + 8) * mult);
            final int attRoll = effAtt * (styleEquipBonus + 64);
            final int defRoll = (monster.getDefenceLevel() + 9) * (monsterDef + 64);

            double hit = hitChance(attRoll, defRoll);
            if (weapon != null && weapon.getName().toLowerCase(Locale.ROOT).contains("osmumten's fang")
               && "Stab".equals(t[0])) {
               hit = 1.0 - (1.0 - hit) * (1.0 - hit);
            }

            final int effStr = (int) Math.floor((player.level(Skill.STRENGTH) + 11) * mult);
            int maxHit = (int) Math.floor(0.5 + effStr * (str + 64) / 640.0);
            if (weapon != null && weapon.getName().toLowerCase(Locale.ROOT).contains("osmumten's fang")) {
               maxHit = (int) Math.floor(maxHit * 0.85);
            }
            final double dps = (0.5 * maxHit * hit) / (speed * 0.6);
            if (best == null || dps > best.getDps()) {
               best = new GearSetup(CombatStyle.MELEE, orderSlots(equipped), t[0] + ", aggressive",
                  maxHit, hit, dps, speed, Collections.emptyList());
            }
         }
      }
      return best;
   }

   private static double meleeSlotScore(final OwnedGear g, final MonsterStats monster, final boolean onTask) {
      final String n = g.getName().toLowerCase(Locale.ROOT);
      if (g.getSlot() == AMULET && monster.is("undead") && n.contains("salve amulet")) {
         return 10_000 + (n.contains("(e") ? 500 : 0);
      }
      if (g.getSlot() == HEAD && onTask && (n.contains("slayer helmet") || n.contains("black mask"))) {
         return 9_000;
      }
      final ItemEquipmentStats s = g.getStats();
      return s.getStr() * 4.0 + maxOf(s.getAstab(), s.getAslash(), s.getAcrush())
         + (s.getDstab() + s.getDslash() + s.getDcrush()) * 0.05 + s.getPrayer() * 0.5;
   }

   private static double meleeMultiplier(final Map<Integer, OwnedGear> equipped, final OwnedGear weapon,
      final MonsterStats monster, final boolean onTask) {
      double mult = 1.0;
      final String amulet = nameIn(equipped, AMULET);
      final String head = nameIn(equipped, HEAD);
      if (monster.is("undead") && amulet.contains("salve amulet")) {
         mult *= amulet.contains("(e") ? 1.20 : 7.0 / 6.0;
      } else if (onTask && (head.contains("slayer helmet") || head.contains("black mask"))) {
         mult *= 7.0 / 6.0;
      }
      if (weapon != null) {
         final String w = weapon.getName().toLowerCase(Locale.ROOT);
         if (monster.is("dragon") && w.contains("dragon hunter lance")) {
            mult *= 1.20;
         } else if (monster.is("demon") && (w.contains("arclight") || w.contains("emberlight"))) {
            mult *= 1.70;
         }
      }
      return mult;
   }

   // ---------------------------------------------------------------------------------------------
   // Ranged
   // ---------------------------------------------------------------------------------------------

   private GearSetup bestRanged(final MonsterStats monster, final Map<Integer, List<OwnedGear>> bySlot,
      final PlayerSnapshot player, final boolean onTask) {
      final List<OwnedGear> weapons = new ArrayList<>();
      for (final OwnedGear w : bySlot.getOrDefault(WEAPON, Collections.emptyList())) {
         if (isRangedWeapon(w)) {
            weapons.add(w);
         }
      }
      if (weapons.isEmpty()) {
         return null;
      }

      final Map<Integer, OwnedGear> base = new LinkedHashMap<>();
      for (final int slot : SLOT_ORDER) {
         if (slot == WEAPON || slot == SHIELD || slot == AMMO) {
            continue;
         }
         final OwnedGear best = pickBest(bySlot.get(slot), g -> rangedSlotScore(g, monster, onTask));
         if (best != null) {
            base.put(slot, best);
         }
      }

      final OwnedGear bestShield = pickShield(bySlot.get(SHIELD), monster,
         g -> g.getStats().getRstr() * 4.0 + g.getStats().getArange());
      final List<OwnedGear> ammos = bySlot.getOrDefault(AMMO, Collections.emptyList());

      GearSetup best = null;
      for (final OwnedGear weapon : weapons) {
         final int kind = rangedKind(weapon);
         final Map<Integer, OwnedGear> equipped = new LinkedHashMap<>(base);
         equipped.put(WEAPON, weapon);

         final boolean twoHanded = weapon.getStats().isTwoHanded();
         if (!twoHanded && bestShield != null) {
            equipped.put(SHIELD, bestShield);
         } else if (twoHanded && monster.is("dragon") && bestShield != null && isDragonfireShield(bestShield.getName())) {
            continue;
         }

         final OwnedGear ammo = pickAmmo(kind, ammos);
         if ((kind == KIND_BOW || kind == KIND_CROSSBOW) && ammo == null && !ammos.isEmpty()) {
            continue;
         }
         if (ammo != null) {
            equipped.put(AMMO, ammo);
         }

         final int arange = sumInt(equipped.values(), ItemEquipmentStats::getArange);
         final int rawRstr = sumInt(equipped.values(), ItemEquipmentStats::getRstr);
         final int rstr = kind == KIND_THROWN && ammo != null
            ? rawRstr - ammo.getStats().getRstr()
            : rawRstr + (weapon.getName().toLowerCase(Locale.ROOT).contains("blowpipe") ? 20 : 0);

         final int baseSpeed = weapon.getStats().getAspeed() <= 0 ? 4 : weapon.getStats().getAspeed();
         final int speed = Math.max(1, baseSpeed - 1); // Rapid

         final double accMult = rangedAccuracyMultiplier(equipped, weapon, monster, onTask);
         final double dmgMult = rangedDamageMultiplier(equipped, weapon, monster, onTask);

         final int effRange = (int) Math.floor((player.level(Skill.RANGED) + 8) * accMult);
         final int attRoll = effRange * (arange + 64);
         final int monsterDef = kind == KIND_THROWN ? monster.getDefLight()
            : kind == KIND_CROSSBOW ? monster.getDefHeavy() : monster.getDefStandard();
         final int defRoll = (monster.getDefenceLevel() + 9) * (monsterDef + 64);

         final double hit = hitChance(attRoll, defRoll);
         final int effStr = (int) Math.floor((player.level(Skill.RANGED) + 8) * dmgMult);
         final int maxHit = (int) Math.floor(0.5 + effStr * (rstr + 64) / 640.0);
         final double dps = (0.5 * maxHit * hit) / (speed * 0.6);

         if (best == null || dps > best.getDps()) {
            best = new GearSetup(CombatStyle.RANGED, orderSlots(equipped), "Rapid",
               maxHit, hit, dps, speed, Collections.emptyList());
         }
      }
      return best;
   }

   private int rangedKind(final OwnedGear weapon) {
      final String n = weapon.getName().toLowerCase(Locale.ROOT);
      if (n.contains("crystal bow") || n.contains("bow of faerdhinen") || n.contains("craw's bow")
         || n.contains("webweaver bow")) {
         return KIND_SELF_AMMO;
      }
      if (n.contains("crossbow") || n.contains("ballista") || n.contains("karil's")) {
         return KIND_CROSSBOW;
      }
      if (n.contains("blowpipe") || n.contains("dart") || n.contains("knife") || n.contains("chinchompa")
         || n.contains("thrownaxe") || n.contains("toktz-xil-ul")) {
         return KIND_THROWN;
      }
      final WikiItem info = wiki.item(weapon.getName());
      if (info != null && info.getCombatStyle() != null) {
         final String cs = info.getCombatStyle().toLowerCase(Locale.ROOT);
         if (cs.contains("crossbow") || cs.contains("ballista")) {
            return KIND_CROSSBOW;
         }
         if (cs.contains("thrown") || cs.contains("chinchompa")) {
            return KIND_THROWN;
         }
      }
      return KIND_BOW;
   }

   private static OwnedGear pickAmmo(final int kind, final List<OwnedGear> ammos) {
      if (kind == KIND_SELF_AMMO || kind == KIND_THROWN) {
         // Blessing or any non-interfering item in the ammo slot for prayer bonus.
         return pickBest(ammos, g -> g.getStats().getRstr() == 0 ? g.getStats().getPrayer() : -1);
      }
      return pickBest(ammos, g -> {
         final String n = g.getName().toLowerCase(Locale.ROOT);
         if (kind == KIND_BOW && n.contains("arrow")) {
            return g.getStats().getRstr() * 4.0 + g.getStats().getArange() + 100;
         }
         if (kind == KIND_CROSSBOW && (n.contains("bolts") || n.contains("bolt rack"))) {
            return g.getStats().getRstr() * 4.0 + g.getStats().getArange() + 100;
         }
         return -1;
      });
   }

   private static double rangedSlotScore(final OwnedGear g, final MonsterStats monster, final boolean onTask) {
      final String n = g.getName().toLowerCase(Locale.ROOT);
      if (g.getSlot() == AMULET && monster.is("undead") && n.contains("salve amulet") && n.contains("i)")) {
         return 10_000 + (n.contains("(ei)") ? 500 : 0);
      }
      if (g.getSlot() == HEAD && onTask && (n.contains("slayer helmet (i)") || n.contains("black mask (i)"))) {
         return 9_000;
      }
      final ItemEquipmentStats s = g.getStats();
      return s.getRstr() * 4.0 + s.getArange() + s.getPrayer() * 0.5;
   }

   private static double rangedAccuracyMultiplier(final Map<Integer, OwnedGear> equipped, final OwnedGear weapon,
      final MonsterStats monster, final boolean onTask) {
      double mult = baseRangedSlayerUndead(equipped, monster, onTask);
      final String w = weapon.getName().toLowerCase(Locale.ROOT);
      if (monster.is("dragon") && w.contains("dragon hunter crossbow")) {
         mult *= 1.25;
      } else if (monster.is("demon") && w.contains("scorching bow")) {
         mult *= 1.30;
      } else if (w.contains("twisted bow")) {
         final int m = Math.min(250, Math.max(monster.getMagicLevel(), monster.getMagicAttack()));
         final double pct = 140.0 + (10.0 * 3.0 * m / 10.0 - 10.0) / 100.0
            - Math.pow(3.0 * m / 10.0 - 100.0, 2) / 100.0;
         mult *= Math.max(0.5, Math.min(1.40, pct / 100.0));
      }
      return mult;
   }

   private static double rangedDamageMultiplier(final Map<Integer, OwnedGear> equipped, final OwnedGear weapon,
      final MonsterStats monster, final boolean onTask) {
      double mult = baseRangedSlayerUndead(equipped, monster, onTask);
      final String w = weapon.getName().toLowerCase(Locale.ROOT);
      if (monster.is("dragon") && w.contains("dragon hunter crossbow")) {
         mult *= 1.20;
      } else if (monster.is("demon") && w.contains("scorching bow")) {
         mult *= 1.30;
      } else if (w.contains("twisted bow")) {
         final int m = Math.min(350, Math.max(monster.getMagicLevel(), monster.getMagicAttack()));
         final double pct = 250.0 + (10.0 * 3.0 * m / 10.0 - 14.0) / 100.0
            - Math.pow(3.0 * m / 10.0 - 140.0, 2) / 100.0;
         mult *= Math.max(0.5, Math.min(2.50, pct / 100.0));
      }
      return mult;
   }

   private static double baseRangedSlayerUndead(final Map<Integer, OwnedGear> equipped, final MonsterStats monster,
      final boolean onTask) {
      final String amulet = nameIn(equipped, AMULET);
      final String head = nameIn(equipped, HEAD);
      if (monster.is("undead") && amulet.contains("salve amulet") && amulet.contains("i)")) {
         return amulet.contains("(ei)") ? 1.20 : 1.15;
      }
      if (onTask && (head.contains("slayer helmet (i)") || head.contains("black mask (i)"))) {
         return 1.15;
      }
      return 1.0;
   }

   // ---------------------------------------------------------------------------------------------
   // Magic
   // ---------------------------------------------------------------------------------------------

   private GearSetup bestMagic(final MonsterStats monster, final Map<Integer, List<OwnedGear>> bySlot,
      final PlayerSnapshot player, final boolean onTask) {
      final Map<Integer, OwnedGear> base = new LinkedHashMap<>();
      for (final int slot : SLOT_ORDER) {
         if (slot == WEAPON || slot == SHIELD) {
            continue;
         }
         final OwnedGear best = pickBest(bySlot.get(slot), g -> magicSlotScore(g, monster, onTask));
         if (best != null) {
            base.put(slot, best);
         }
      }

      final OwnedGear bestShield = pickShield(bySlot.get(SHIELD), monster,
         g -> g.getStats().getMdmg() * 4.0 + g.getStats().getAmagic());

      final List<OwnedGear> staves = new ArrayList<>();
      for (final OwnedGear w : bySlot.getOrDefault(WEAPON, Collections.emptyList())) {
         if (w.getStats().getAmagic() > 0 || w.getStats().getMdmg() > 0 || isPoweredStaff(w.getName())) {
            staves.add(w);
         }
      }
      staves.add(null);

      final int magicLvl = player.level(Skill.MAGIC);
      final Object[] bestSpell = pickSpell(magicLvl, monster, nameIn(base, GLOVES).contains("chaos gauntlets"));

      GearSetup best = null;
      for (final OwnedGear weapon : staves) {
         final Map<Integer, OwnedGear> equipped = new LinkedHashMap<>(base);
         if (weapon != null) {
            equipped.put(WEAPON, weapon);
         }
         final boolean twoHanded = weapon != null && weapon.getStats().isTwoHanded();
         if (!twoHanded && bestShield != null) {
            equipped.put(SHIELD, bestShield);
         } else if (twoHanded && monster.is("dragon") && bestShield != null && isDragonfireShield(bestShield.getName())) {
            continue;
         }

         final int rawAmagic = sumInt(equipped.values(), ItemEquipmentStats::getAmagic);
         double rawMdmg = 0;
         for (final OwnedGear g : equipped.values()) {
            rawMdmg += g.getStats().getMdmg();
         }
         final boolean shadow = weapon != null && weapon.getName().toLowerCase(Locale.ROOT).contains("tumeken's shadow");
         final int amagic = shadow ? rawAmagic * 3 : rawAmagic;
         final double mdmgPct = shadow ? Math.min(100.0, rawMdmg * 3.0) : rawMdmg;

         final double mult = magicMultiplier(equipped, weapon, monster, onTask);
         final int effMag = (int) Math.floor((magicLvl + 9) * mult);
         final int attRoll = effMag * (amagic + 64);
         final int defRoll = (monster.getMagicLevel() + 9) * (monster.getDefMagic() + 64);
         final double hit = hitChance(attRoll, defRoll);

         // Option A: powered staff built-in spell.
         if (weapon != null && isPoweredStaff(weapon.getName())) {
            final int baseMax = poweredStaffBaseMax(weapon.getName(), magicLvl);
            final int maxHit = (int) Math.floor(baseMax * (1.0 + mdmgPct / 100.0) * mult);
            final int speed = weapon.getName().toLowerCase(Locale.ROOT).contains("tumeken's shadow") ? 5 : 4;
            final double dps = (0.5 * maxHit * hit) / (speed * 0.6);
            if (best == null || dps > best.getDps()) {
               best = new GearSetup(CombatStyle.MAGIC, orderSlots(equipped), "Built-in spell",
                  maxHit, hit, dps, speed, Collections.emptyList());
            }
         }

         // Option B: best standard elemental spell (great against elemental weaknesses).
         if (bestSpell != null && (weapon == null || !isPoweredStaff(weapon.getName()))) {
            final String spellName = (String) bestSpell[0];
            int baseMax = (int) bestSpell[2];
            if (spellName.endsWith("Bolt") && nameIn(equipped, GLOVES).contains("chaos gauntlets")) {
               baseMax += 3;
            }
            final String element = (String) bestSpell[3];
            final double elemBonus = monster.getWeakness() != null && monster.getWeakness().equalsIgnoreCase(element)
               ? monster.getWeaknessPercent() / 100.0 : 0.0;
            final int maxHit = (int) Math.floor(baseMax * (1.0 + mdmgPct / 100.0 + elemBonus) * mult);
            final double dps = (0.5 * maxHit * hit) / (5 * 0.6);
            if (best == null || dps > best.getDps()) {
               final List<String> runes = Arrays.asList((String[]) bestSpell[4]);
               best = new GearSetup(CombatStyle.MAGIC, orderSlots(equipped), spellName,
                  maxHit, hit, dps, 5, runes);
            }
         }
      }
      return best;
   }

   private static Object[] pickSpell(final int magicLevel, final MonsterStats monster, final boolean chaosGauntlets) {
      Object[] best = null;
      double bestScore = -1;
      for (final Object[] spell : SPELLS) {
         final int req = (int) spell[1];
         if (magicLevel < req) {
            continue;
         }
         final String name = (String) spell[0];
         int base = (int) spell[2];
         if ( chaosGauntlets && name.endsWith("Bolt")) {
            base += 3;
         }
         final String element = (String) spell[3];
         final double mult = monster.getWeakness() != null && monster.getWeakness().equalsIgnoreCase(element)
            ? 1.0 + monster.getWeaknessPercent() / 100.0 : 1.0;
         final double score = base * mult;
         if (score >= bestScore) {
            bestScore = score;
            best = spell;
         }
      }
      return best;
   }

   private static double magicSlotScore(final OwnedGear g, final MonsterStats monster, final boolean onTask) {
      final String n = g.getName().toLowerCase(Locale.ROOT);
      if (g.getSlot() == AMULET && monster.is("undead") && n.contains("salve amulet") && n.contains("i)")) {
         return 10_000 + (n.contains("(ei)") ? 500 : 0);
      }
      if (g.getSlot() == HEAD && onTask && (n.contains("slayer helmet (i)") || n.contains("black mask (i)"))) {
         return 9_000;
      }
      final ItemEquipmentStats s = g.getStats();
      return s.getMdmg() * 4.0 + s.getAmagic() + s.getPrayer() * 0.5;
   }

   private static double magicMultiplier(final Map<Integer, OwnedGear> equipped, final OwnedGear weapon,
      final MonsterStats monster, final boolean onTask) {
      double mult = baseRangedSlayerUndead(equipped, monster, onTask);
      if (weapon != null) {
         final String w = weapon.getName().toLowerCase(Locale.ROOT);
         if (monster.is("dragon") && w.contains("dragon hunter wand")) {
            mult *= 1.20;
         } else if (monster.is("demon") && w.contains("purging staff")) {
            mult *= 1.30;
         }
      }
      return mult;
   }

   private static boolean isPoweredStaff(final String name) {
      final String n = name.toLowerCase(Locale.ROOT);
      return n.contains("trident of the") || n.contains("sanguinesti staff") || n.contains("tumeken's shadow")
         || n.contains("warped sceptre") || n.contains("accursed sceptre") || n.contains("thammaron's sceptre")
         || n.contains("bone staff") || n.contains("crystal staff");
   }

   private static int poweredStaffBaseMax(final String name, final int magicLvl) {
      final String n = name.toLowerCase(Locale.ROOT);
      if (n.contains("tumeken's shadow")) {
         return Math.max(1, magicLvl / 3 + 1);
      }
      if (n.contains("sanguinesti")) {
         return Math.max(1, magicLvl / 3 - 1);
      }
      if (n.contains("trident of the swamp")) {
         return Math.max(1, magicLvl / 3 - 2);
      }
      if (n.contains("trident of the seas")) {
         return Math.max(1, magicLvl / 3 - 5);
      }
      return Math.max(1, (8 * magicLvl - 96) / 36);
   }

   // ---------------------------------------------------------------------------------------------
   // Candidate filtering & helpers
   // ---------------------------------------------------------------------------------------------

   /**
    * For each slot, picks the top candidates across Melee, Ranged, Magic and Defence, then verifies on
    * the OSRS Wiki that the player has the skill levels to wear them.
    */
   private Map<Integer, List<OwnedGear>> filterWearable(final PlayerSnapshot player, final Set<String> hiddenOut) {
      final Map<Integer, List<OwnedGear>> rawBySlot = new LinkedHashMap<>();
      for (final OwnedGear g : player.getOwnedGear()) {
         rawBySlot.computeIfAbsent(g.getSlot(), k -> new ArrayList<>()).add(g);
      }

      final Map<Integer, List<OwnedGear>> wearable = new LinkedHashMap<>();
      for (final Map.Entry<Integer, List<OwnedGear>> e : rawBySlot.entrySet()) {
         final Set<Integer> topIds = new LinkedHashSet<>();
         final List<OwnedGear> candidates = new ArrayList<>();
         final List<OwnedGear> slotItems = e.getValue();

         addTop(slotItems, g -> g.getStats().getStr() * 4.0
            + maxOf(g.getStats().getAstab(), g.getStats().getAslash(), g.getStats().getAcrush()), topIds, candidates);
         addTop(slotItems, g -> g.getStats().getRstr() * 4.0 + g.getStats().getArange(), topIds, candidates);
         addTop(slotItems, g -> g.getStats().getMdmg() * 4.0 + g.getStats().getAmagic(), topIds, candidates);
         addTop(slotItems, g -> isSpecialGear(g.getName()) ? 1000 : 0, topIds, candidates);

         final List<OwnedGear> passed = new ArrayList<>();
         for (final OwnedGear g : candidates) {
            final String missingReq = firstUnmetRequirement(g, player);
            if (missingReq == null) {
               passed.add(g);
            } else {
               hiddenOut.add(g.getName() + " (needs " + missingReq + ")");
            }
         }
         wearable.put(e.getKey(), passed);
      }
      return wearable;
   }

   private static void addTop(final List<OwnedGear> items, final java.util.function.ToDoubleFunction<OwnedGear> score,
      final Set<Integer> seenIds, final List<OwnedGear> out) {
      final List<OwnedGear> copy = new ArrayList<>(items);
      copy.sort((a, b) -> Double.compare(score.applyAsDouble(b), score.applyAsDouble(a)));
      int added = 0;
      for (final OwnedGear g : copy) {
         if (added >= TOP_CANDIDATES_PER_SLOT) {
            break;
         }
         if (seenIds.add(g.getId())) {
            out.add(g);
         }
         added++;
      }
   }

   /** Returns e.g. "70 Attack" if the player doesn't meet the wiki's level requirement, or null if wearable. */
   private String firstUnmetRequirement(final OwnedGear gear, final PlayerSnapshot player) {
      // If the player is already wearing it, they definitely meet the requirements.
      if (gear.getSource() == com.kamrant.runegpt.model.ItemSource.WORN) {
         return null;
      }
      final WikiItem info = wiki.item(gear.getName());
      if (info == null || info.getRequirements().isEmpty()) {
         return null;
      }
      for (final Map.Entry<String, Integer> req : info.getRequirements().entrySet()) {
         final Skill skill = skillByName(req.getKey());
         if (skill != null && player.level(skill) < req.getValue()) {
            return req.getValue() + " " + skill.getName();
         }
      }
      return null;
   }

   private static Skill skillByName(final String name) {
      for (final Skill s : Skill.values()) {
         if (s.getName().equalsIgnoreCase(name)) {
            return s;
         }
      }
      return null;
   }

   private static boolean isSpecialGear(final String name) {
      final String n = name.toLowerCase(Locale.ROOT);
      return n.contains("salve") || n.contains("slayer helmet") || n.contains("black mask")
         || n.contains("anti-dragon") || n.contains("dragonfire") || n.contains("wyvern shield")
         || n.contains("dragon hunter") || n.contains("arclight") || n.contains("emberlight")
         || n.contains("scorching bow") || n.contains("purging staff") || n.contains("twisted bow")
         || n.contains("tumeken") || n.contains("trident") || n.contains("sanguinesti")
         || n.contains("warped sceptre") || n.contains("chaos gauntlets") || n.contains("osmumten");
   }

   private static OwnedGear pickShield(final List<OwnedGear> shields, final MonsterStats monster,
      final java.util.function.ToDoubleFunction<OwnedGear> styleScore) {
      if (shields == null || shields.isEmpty()) {
         return null;
      }
      return pickBest(shields, g -> {
         final double dragonBonus = monster.is("dragon") && isDragonfireShield(g.getName()) ? 5_000 : 0;
         return dragonBonus + styleScore.applyAsDouble(g);
      });
   }

   private static boolean isDragonfireShield(final String name) {
      final String n = name.toLowerCase(Locale.ROOT);
      return n.contains("anti-dragon shield") || n.contains("dragonfire shield")
         || n.contains("dragonfire ward") || n.contains("ancient wyvern shield");
   }

   private static boolean isRangedWeapon(final OwnedGear w) {
      final String n = w.getName().toLowerCase(Locale.ROOT);
      return w.getStats().getArange() > 0 || n.contains("blowpipe") || n.contains("bow")
         || n.contains("dart") || n.contains("knife") || n.contains("chinchompa") || n.contains("ballista");
   }

   private static boolean isRangedOrMagicOnlyWeapon(final OwnedGear w) {
      final ItemEquipmentStats s = w.getStats();
      final int melee = maxOf(s.getAstab(), s.getAslash(), s.getAcrush());
      return (isRangedWeapon(w) || isPoweredStaff(w.getName())) && melee <= 0 && s.getStr() <= 0;
   }

   private static OwnedGear pickBest(final List<OwnedGear> items,
      final java.util.function.ToDoubleFunction<OwnedGear> score) {
      if (items == null || items.isEmpty()) {
         return null;
      }
      OwnedGear best = null;
      double bestScore = -0.001;
      for (final OwnedGear g : items) {
         final double s = score.applyAsDouble(g);
         if (s > bestScore) {
            bestScore = s;
            best = g;
         }
      }
      return best;
   }

   private static Map<Integer, OwnedGear> orderSlots(final Map<Integer, OwnedGear> map) {
      final Map<Integer, OwnedGear> ordered = new LinkedHashMap<>();
      for (final int slot : SLOT_ORDER) {
         final OwnedGear g = map.get(slot);
         if (g != null) {
            ordered.put(slot, g);
         }
      }
      return ordered;
   }

   /** Exact OSRS accuracy formula for attack roll vs defence roll. */
   public static double hitChance(final int attackRoll, final int defenceRoll) {
      final int a = Math.max(0, attackRoll);
      final int d = Math.max(0, defenceRoll);
      if (a > d) {
         return 1.0 - (d + 2.0) / (2.0 * (a + 1.0));
      }
      return a / (2.0 * (d + 1.0));
   }

   public static String slotName(final int slot) {
      if (slot == HEAD) {
         return "head";
      }
      if (slot == EquipmentInventorySlot.CAPE.getSlotIdx()) {
         return "cape";
      }
      if (slot == AMULET) {
         return "neck";
      }
      if (slot == WEAPON) {
         return "weapon";
      }
      if (slot == EquipmentInventorySlot.BODY.getSlotIdx()) {
         return "body";
      }
      if (slot == SHIELD) {
         return "shield";
      }
      if (slot == EquipmentInventorySlot.LEGS.getSlotIdx()) {
         return "legs";
      }
      if (slot == GLOVES) {
         return "hands";
      }
      if (slot == EquipmentInventorySlot.BOOTS.getSlotIdx()) {
         return "feet";
      }
      if (slot == EquipmentInventorySlot.RING.getSlotIdx()) {
         return "ring";
      }
      if (slot == AMMO) {
         return "ammo";
      }
      return "gear";
   }

   private static String nameIn(final Map<Integer, OwnedGear> equipped, final int slot) {
      final OwnedGear g = equipped.get(slot);
      return g == null ? "" : g.getName().toLowerCase(Locale.ROOT);
   }

   private static int maxOf(final int a, final int b, final int c) {
      return Math.max(a, Math.max(b, c));
   }

   private static int sumInt(final Iterable<OwnedGear> items,
      final java.util.function.ToIntFunction<ItemEquipmentStats> getter) {
      int total = 0;
      for (final OwnedGear g : items) {
         total += getter.applyAsInt(g.getStats());
      }
      return total;
   }
}
